# Benchmark results

Results for each release on the XCSP3 benchmark set in
[`src/test/resources/xcsp3/benchmark/`](../src/test/resources/xcsp3/benchmark): 73 instances from
the 2022–2025 XCSP3 competitions, at most three per family. Newest release first.

## The set

Tiers were chosen by how jcsp did on each instance across three seeds before 5.1.0:

- **quick**: solved in under 5s on every seed;
- **perf**: solved in 5–60s on every seed;
- **gap**: not solved on every seed, though Choco solves most of them.

Each family sits wholly in one half: tune against **measured**, then confirm on **heldout**, which
a change was not tuned on. [`manifest.tsv`](../src/test/resources/xcsp3/benchmark/manifest.tsv)
records each instance's tier and the jcsp and Choco 6.0.1 results it was chosen from.

## How to refresh

```bash
mvn test-compile
java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
    io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CompetitionRunner src/test/resources/xcsp3/benchmark 60
```

The run ends with the table below, ready to paste under a new release heading. It takes about 20
minutes. Each instance runs in its own JVM with a 60s limit and the runner's fixed restart seed,
so a rerun of the same build on the same machine explores the same search, but times move by a
few percent between runs, and a sub-second time by much more. Compare releases on the same
machine; "Answered" counts a solution, an optimum or a proof of unsatisfiability.

## 5.1.0

Apple M5 (10 cores, 32 GB), JDK 25.0.2.

| Folder | Instances | Answered | Solve time (s) |
|---|---:|---:|---:|
| heldout/gap | 8 | 5 | 312.0 |
| heldout/perf | 12 | 12 | 248.0 |
| heldout/quick | 17 | 17 | 20.6 |
| measured/gap | 7 | 4 | 266.6 |
| measured/perf | 13 | 13 | 232.1 |
| measured/quick | 16 | 16 | 25.4 |
| **Total** | 73 | 67 | 1104.6 |

8 of the 13 optimization instances were proven optimal. The runner reports one
`SolutionChecker` mismatch, on `measured/gap/RotatingRostering-044-2-3`: a bug in `xcsp3-tools`
2.5's checker, which reads `imp(eq(a,b,c),y)` as though it were
`or(ne(a,b,c),y)` with `ne` meaning all-different
([xcsp3team/XCSP3-Java-Tools#24](https://github.com/xcsp3team/XCSP3-Java-Tools/issues/24)).
Every constraint it flags holds.
