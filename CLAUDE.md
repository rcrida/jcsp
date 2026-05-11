# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.1.0</version>
</dependency>
```

## Publishing

```bash
mvn deploy    # Sign, package, and publish to Maven Central
```

Requires GPG key and Maven Central token in `~/.m2/settings.xml` under server id `central`.

When creating a new release: bump the version in `pom.xml`, update `README.md` (installation version + any new features/API changes), commit, tag, push, and create a GitHub release.

## Build & Test Commands

```bash
mvn compile                          # Compile sources
mvn test                             # Run all tests
mvn test -Dtest=ClassName            # Run a single test class
mvn verify                           # Build with JaCoCo coverage report
```

Coverage report is generated at `target/site/jacoco/index.html`. **100% instruction and branch coverage is enforced** — the build fails if any code is not covered.

## Architecture Overview

This is a Constraint Satisfaction Problem (CSP) solver library implementing classic AI algorithms. The core flow is: define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.create(csp).getSolutions()` to get a lazy `Stream` of `Assignment` solutions.

### Core Abstractions

- **`Variable`** — immutable identifier; created via `Variable.Factory`
- **`Domain`** — set of allowed values for a variable (`IntRangeDomain`, `EnumDomain`, `AssignmentDomain`, `ObjectSetDomain`)
- **`Assignment`** — immutable mapping of variables to values; validated against domains and constraints
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — hierarchical constraint interfaces; each checks `isSatisfiedBy(Assignment)`
- **`ConstraintSatisfactionProblem`** — aggregates variables, domains, constraints; analyzes graph structure (tree/cyclic, connected components, cutsets)

### Solver Chain (Decorator Pattern)

`Solver.Factory.create()` builds a chain of solver decorators, each applied in order before delegating to the next:

1. **`NodeConsistentSolver`** — prunes domains via node consistency
2. **`ArcConsistentSolver`** — applies AC3 arc consistency
3. **`IndependentSubproblemSolver`** — decomposes into independent subproblems and combines solutions
4. **`TreeDecompositionSolver`** — applies tree decomposition for near-tree problems
5. **`CutsetConditioningSolver`** — handles cyclic graphs by conditioning on a cycle cutset
6. **`TreeSolver`** / **`BacktrackingSearch`** — terminal solvers for tree-structured or general CSPs respectively

`BacktrackingSearch` uses pluggable strategies: `UnassignedVariableSelector` (with `MinimumRemainingValuesSelector`), `DomainValuesOrderer` (with `LeastConstrainingValueOrderer`), and `Inference` (AC3 or node consistency).

### Constraint Construction

`CSP.Builder` provides fluent helper methods to avoid constructing constraint objects directly:

```java
csp.equalsConstraint(v1, v2)
csp.notEqualsConstraint(v1, v2)
csp.notEqualsChainConstraint(v1, v2, v3, ...)   // AllDiff over a chain
csp.allDiffConstraint(v1, v2, ...)
csp.offsetConstraint(v1, v2, offset)            // v1 = v2 + offset
csp.biPredicateConstraint(v1, v2, predicate)
csp.predicateConstraint(predicate, v1, v2, ...) // n-ary predicate
```

### Key Conventions

- **Immutability**: `Assignment`, `Variable`, and constraint objects use Lombok `@Value`; `CSP` uses `@Builder`/`@Singular`. Use `@Value` for any new immutable class — it provides `@ToString`, `@EqualsAndHashCode`, `@RequiredArgsConstructor`, and final fields automatically.
- **Lombok**: `@Value`, `@Builder`, `@SuperBuilder`, `@Singular`, `@Slf4j` are used extensively — do not add manual boilerplate that Lombok already provides
- **Null safety**: JSpecify `@NonNull`/`@Nullable` annotations throughout; `Optional` used for nullable returns
- **Logging**: All solvers/consistency algorithms use `@Slf4j` (SLF4J) for debug/info logging
- **Assertions**: Domain validity in `Assignment` is checked with Java `assert` statements

### Integration Tests

Classic CSP problems serve as end-to-end integration tests:
- `AustraliaMapColouringTest` — graph coloring
- `NQueensTest` — N-Queens placement
- `SudokuTest` — Sudoku solving
- `CryptarithmeticTest` — alphametic puzzle solving
