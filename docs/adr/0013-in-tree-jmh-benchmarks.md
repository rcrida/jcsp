# 0013. In-tree JMH benchmarks, not a separate module

**Status**: Accepted

## Context

`NogoodPropagationBenchmark` (`io.github.rcrida.jcsp.benchmark`, `src/test/java`) is a hand-rolled,
non-JUnit `main()` class measuring CDCL/nogood-store overhead. Adding a second, complementary
benchmark suite — `CsplibBenchmarks`, covering the full solver chain end-to-end across the CSPLib
problems in `solver.examples.csplib` — needed statistical rigor (warmup, forking,
dead-code-elimination safety) that hand-rolled timing doesn't provide, motivating JMH.

The standard JMH setup is a dedicated Maven module: `maven-shade-plugin` builds a self-contained
`benchmarks.jar` with `org.openjdk.jmh.Main` as its entry point, so the suite can be run or
distributed independently of the rest of the build. This repo, however, is a single-module library
whose root `pom.xml` is also the exact artifact signed and published to Maven Central (`mvn
deploy`, GPG signing via `maven-gpg-plugin`, `central-publishing-maven-plugin`). Converting it to a
multi-module reactor (`<packaging>pom</packaging>` plus `<modules>`) to host a `benchmarks/` child
module touches the same POM that drives releases, for a benefit — a portable, independently
distributable jar — that doesn't matter for a benchmark suite only ever run locally in this
checkout.

## Decision

Add JMH in-tree: `jmh-core`/`jmh-generator-annprocess` (both `test`-scope) in the existing root
`pom.xml`, with `jmh-generator-annprocess` wired into `maven-compiler-plugin`'s
`annotationProcessorPaths` alongside Lombok so `mvn test-compile` generates JMH's runner classes
into `target/test-classes` automatically. `CsplibBenchmarks` lives in `src/test/java` next to the
CSPLib test classes it reuses, named without a `Test` suffix so surefire's default include pattern
(`**/*Test.java`) skips it — the same mechanism that already excludes `NogoodPropagationBenchmark`.
No shade plugin, no new module, no change to the root POM's packaging.

Run via `mvn test-compile` then `java -cp target/classes:target/test-classes:$(mvn -q
dependency:build-classpath -Dmdep.outputFile=/dev/stdout) org.openjdk.jmh.Main CsplibBenchmarks` —
the same classpath-assembly style `NogoodPropagationBenchmark`'s own run instructions already use.

## Rejected alternatives

- **Separate `benchmarks/` child module with `maven-shade-plugin`.** The conventional JMH setup,
  rejected specifically because it requires restructuring the root `pom.xml` that Maven Central
  publishing depends on, for a portability benefit (a self-contained `benchmarks.jar`, runnable
  outside this checkout) that isn't needed today. Revisit if the benchmarks ever need to run
  somewhere other than a local checkout of this repo — e.g. as a CI artifact, or handed to someone
  else without the full dev environment.

## Consequences

- Extracting to a separate module later is a mechanical, low-risk migration, not a rewrite: move
  the benchmark classes and the JMH test-scope dependencies into a new `benchmarks/pom.xml`, add
  `maven-shade-plugin` there, flip the root `pom.xml` to `<packaging>pom</packaging>` with
  `<modules>`. The one real code change at that point is that any CSPLib builder method
  `CsplibBenchmarks` reuses package-privately today would need to become `public`, since cross-module
  access replaces same-package access.
- Both benchmark suites now share one convention (plain class in `src/test/java`, excluded from
  surefire by name, run via a hand-assembled classpath) rather than JMH suites and hand-rolled
  suites following different structural rules.
