# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.16.0</version>
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

This is a Constraint Satisfaction Problem (CSP) solver library implementing classic AI algorithms. The core flow is: define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.INSTANCE.createSolver().getSolutions(csp)` to get a lazy `Stream` of `Assignment` solutions.

### Core Abstractions

- **`Variable`** — immutable identifier; created via `Variable.Factory`
- **`Domain`** — set of allowed values for a variable (`IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `DomainObjectSet`)
- **`Assignment`** — immutable mapping of variables to values; validated against domains and constraints
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — hierarchical constraint interfaces; each checks `isSatisfiedBy(Assignment)`
- **`ConstraintSatisfactionProblem`** — aggregates variables, domains, constraints; analyzes graph structure (tree/cyclic, connected components, cutsets)

### Solver Chain (Decorator Pattern)

`Solver.Factory.INSTANCE.createSolver()` builds a chain of solver decorators, each applied in order before delegating to the next:

1. **`NodeConsistentSolver`** — prunes domains via node consistency
2. **`PropagationFixpointSolver`** — runs AC3, AllDiff GAC (Régin 1994), SumConstraint bounds propagation, LinearConstraint bounds propagation, CountConstraint value propagation, InverseConstraint arc consistency, and AmongConstraint value-set propagation in a combined fixpoint loop; each propagator can enable the others to make further reductions. Many highly-constrained problems (Zebra, Sudoku, MagicSquare) are solved entirely at this step.
3. **`CumulativeConsistentSolver`** — applies timetabling propagation for `CumulativeConstraint` instances; no-op if none present
4. **`IndependentSubproblemSolver`** — decomposes into independent subproblems and combines solutions
5. **`TreeDecompositionSolver`** — applies tree decomposition for near-tree problems; skipped when constraint graph minimum degree ≥ targetTreewidth (exact early exit)
6. **`CutsetConditioningSolver`** — handles cyclic graphs by conditioning on a cycle cutset
7. **`TreeSolver`** / **`BacktrackingSearch`** — terminal solvers for tree-structured or general CSPs respectively

`SolverDecorator.getSolutions()` short-circuits immediately when any preprocessing step reduces all domains to singletons, returning the forced assignment without invoking downstream stages.

`BacktrackingSearch` uses pluggable strategies: `UnassignedVariableSelector` (with `MinimumRemainingValuesSelector`), `DomainValuesOrderer` (with `LeastConstrainingValueOrderer`), and `Inference` (MAC + SumConsistency bounds propagation — detecting sum infeasibility as early as possible during search).

The individual `ArcConsistentSolver` and `AllDiffConsistentSolver` decorators still exist for custom chains but are not in the default chain (replaced by `PropagationFixpointSolver`).

### Local Search Chain

`LocalSolver.Factory.INSTANCE.createLocalSolver(maxAttempts, maxSteps, factory)` builds:

```
NodeConsistency → AC3 → IndependentSubproblems → MinConflicts
```

Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory`.

The preprocessing chain before `MinConflicts` is: `NodeConsistency → AC3 → SumConsistency → LinearConsistency → CountConsistency → InverseConsistency → AmongConsistency`. This detects infeasibility early (avoiding wasted attempts) and prunes domains to improve initial assignment quality. `AllDiffConsistency` (GAC) is intentionally excluded — it is expensive and repair-based search does not benefit from that level of arc consistency.

### Constraint Construction

`CSP.Builder` provides fluent helper methods. All binary constraint classes also have static `of()` factory methods.

**Unary**
```java
csp.equalsConstraint(v, value)
csp.notEqualsConstraint(v, value)
csp.predicateConstraint(v, predicate)
csp.comparatorConstraint(v, Operator.GEQ, value)   // v >= value (Number types)
```

**Binary**
```java
csp.equalsConstraint(v1, v2)
csp.notEqualsConstraint(v1, v2)
csp.notEqualsChainConstraint(List.of(v1, v2, v3))  // consecutive pairs differ
csp.offsetConstraint(v1, offset, Operator.EQ, v2)  // v1 + offset == v2
csp.comparatorConstraint(v1, Operator.LEQ, v2)     // v1 <= v2 (any Comparable)
csp.logicConstraint(b1, LogicOperator.OR, b2)       // boolean connective (AND/OR/XOR/NAND/NOR/XNOR)
csp.elementConstraint(index, result, array)         // result = array[index] (1-based)
csp.biPredicateConstraint(v1, v2, predicate)
```

**N-ary**
```java
csp.allDiffConstraint(Set.of(v1, v2, v3))
csp.atMostOneConstraint(Set.of(b1, b2, b3))        // AC3 decomposition into BinaryLogicConstraint(NAND)
csp.atMostNConstraint(Set.of(b1, b2, b3), n)
csp.atLeastNConstraint(Set.of(b1, b2, b3), n)      // prefer for local search
csp.atLeastNConstraintWithCounting(Set.of(b1, b2, b3), n)  // prefer for backtracking (carry-chain)
csp.exactlyOneConstraint(Set.of(b1, b2, b3))
csp.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)
csp.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, 10)  // weighted sum
csp.countConstraint(Set.of(v1, v2, v3), value, Operator.EQ, 2)
csp.amongConstraint(Set.of(v1, v2, v3), Set.of(a, b), Operator.EQ, 2)  // count vars with value in {a,b}
csp.inverseConstraint(List.of(f1, f2, f3), List.of(g1, g2, g3))        // f[i]==j ↔ g[j-1]==i+1
csp.globalCardinalityConstraint(Set.of(v1, v2, v3), Map.of(a, 2, b, 1))
csp.cumulativeConstraint(starts, durations, resources, limit)  // resource scheduling
csp.tuplesConstraint(Set.of(Assignment.of(...), ...))          // extensional (table)
csp.increasingConstraint(List.of(v1, v2, v3))      // v1 <= v2 <= v3; AC3 decomposition
csp.decreasingConstraint(List.of(v1, v2, v3))      // v1 >= v2 >= v3; AC3 decomposition
csp.lexConstraint(List.of(a1, a2), Operator.LEQ, List.of(b1, b2))
csp.predicateConstraint(Set.of(v1, v2, v3), predicate)
```

**Reification**
```java
csp.reifyConstraint(b, constraint)    // b <-> constraint
csp.impliesConstraint(b, constraint)  // b -> constraint
```

### Key Conventions

- **Immutability**: `Assignment`, `Variable`, and constraint objects use Lombok `@Value`; `CSP` uses `@Builder`/`@Singular`. Constraint subclasses use `@SuperBuilder` + `@EqualsAndHashCode(callSuper = true)`.
- **Lombok**: `@Value`, `@Builder`, `@SuperBuilder`, `@Singular`, `@Slf4j` are used extensively — do not add manual boilerplate that Lombok already provides
- **Static factories**: All constraint classes have a static `of()` factory method; use these instead of `.builder()...build()` in production code
- **Null safety**: JSpecify `@NonNull`/`@Nullable` annotations throughout; `Optional` used for nullable returns
- **Logging**: All solvers/consistency algorithms use `@Slf4j` (SLF4J) for debug/info logging
- **Assertions**: Preconditions (e.g., equal list sizes) are checked with Java `assert` statements
- **`Operator` enum** — in `constraints` package; covers EQ, NEQ, LT, GT, LEQ, GEQ
- **`LogicOperator` enum** — in `constraints` package; covers AND, OR, XOR, NAND, NOR, XNOR
- **`ConstraintConsistency` interface** — `@FunctionalInterface` in `consistency` package; the common contract for all propagation/consistency passes: `apply(ConstraintSatisfactionProblem) → Optional<ConstraintSatisfactionProblem>`. All nine consistency classes implement it (`AC3`, `AllDiffConsistency`, `SumConsistency`, `LinearConsistency`, `CountConsistency`, `InverseConsistency`, `AmongConsistency`, `NodeConsistency`, `CumulativeConsistency`). `PropagationFixpointSolver` iterates a static `PROPAGATORS` list; `LocalSolver.Factory` iterates a static `PREPROCESSORS` list — adding a new propagator to either chain is a one-line change.
- **`FixpointConsistency`** — abstract base class in `consistency` package; implements `ConstraintConsistency` for the common pattern of filtering constraints by type and running them to fixpoint. Owns the fixpoint loop (formerly `ConsistencyFixpoint`, now deleted). The seven type-specific consistency classes (`AllDiffConsistency`, `SumConsistency`, `LinearConsistency`, `CountConsistency`, `InverseConsistency`, `AmongConsistency`, `CumulativeConsistency`) each extend it with a two-line constructor: `super(XxxConstraint.class)`. The `Logger` is acquired via `LoggerFactory.getLogger(getClass())` so each subclass logs under its own class name.
- **`Propagatable` interface** — in `consistency` package; constraints that support domain propagation implement `propagate(Map<Variable<?>, Domain<?>> domains) → Optional<Map<Variable<?>, Domain<?>>>`. `AllDiffConstraint`, `SumConstraint`, `LinearConstraint`, `CountConstraint`, `InverseConstraint`, `AmongConstraint`, and `CumulativeConstraint` implement it. Each has a corresponding `FixpointConsistency` subclass that drives propagation and is registered in the solver chains. `CountConstraint` and `AmongConstraint` propagation classifies variables as definite/possible/impossible, then prunes domains when the count quota is met (EQ/LEQ) or must be reached (EQ/GEQ). `InverseConstraint` propagation runs two passes of pairwise arc consistency between the `f` and `invf` arrays.
- **`BinaryDecomposable` interface** — in `constraints` package; n-ary constraints that can be decomposed into an equivalent set of binary constraints implement `getAsBinaryConstraints() → Set<BinaryConstraint<?,?>>`. `AllDiffConstraint`, `AtMostOneConstraint` (and `ExactlyOneConstraint`), `IncreasingConstraint`, `DecreasingConstraint`, and `ReifiedConstraint` implement it. Used by `ConstraintGraph` to infer additional binary constraints for AC3, and by `ConstraintSatisfactionProblem`/`MinConflictsSolver` to identify non-decomposable n-ary constraints. `ReifiedConstraint` returns an empty set when its body is not a `UnaryConstraint`.

### Integration Tests

Classic CSP problems serve as end-to-end integration tests:
- `AustraliaMapColouringTest` — graph coloring; also demonstrates `countConstraint` and `globalCardinalityConstraint`
- `NQueensTest` — N-Queens placement; also demonstrates `increasingConstraint` for symmetry breaking
- `MagicSquareTest` — magic square; demonstrates `sumConstraint` and `lexConstraint` for symmetry breaking
- `SudokuTest` — Sudoku solving
- `CryptarithmeticTest` — alphametic puzzle solving
- `ZebraPuzzleTest` — Einstein's Zebra puzzle
- `TwoSumTest` — two-sum via `elementConstraint`
- `KnapsackTest` — binary knapsack via `linearConstraint` (feasibility + optimisation)
- `MenuCombinationTest` — extensional constraints via `tuplesConstraint`
- `SprintSchedulingTest` — resource-constrained scheduling via `cumulativeConstraint`
- `ReificationTest` — soft constraints via `reifyConstraint` / `impliesConstraint`
- `ParkrunSchedulingTest` / `TimetableSchedulingBinaryAssignmentTest` — real-world scheduling
