# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.26.0</version>
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

This is a Constraint Satisfaction Problem (CSP) solver library implementing classic AI algorithms. The core flow is: define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.INSTANCE.createSolver(csp).getSolutions()` to get a lazy `Stream` of `Assignment` solutions.

### Core Abstractions

- **`Variable`** — immutable identifier; created via `Variable.Factory`
- **`Domain`** — base interface for all domains; defines `contains()`, `isEmpty()`, `size()`, and default `isSingleton()` (via `size() == 1`); `singleValue()` is abstract (discrete domains default it via `stream()`, bounded domains implement it directly)
- **`DiscreteDomain<T> extends Domain<T>`** — enumerable domains (`IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `DomainObjectSet`); adds `stream()`, `toList()`, and `toBuilder()` (with inner `Builder<T>` interface). Default `singleValue()` uses `stream()`. Code that needs to enumerate values should be typed to `DiscreteDomain`, not `Domain`
- **`BoundedDomain<T extends Number & Comparable<T>>`** — `Domain` extension for non-enumerable continuous ranges; `getMin()`/`getMax()`/`withBounds(newMin, newMax)`. `IntervalDomain` is the sole implementation: a `[min, max]` range of `double`s representing a real-valued variable; `size()` returns `1` for a singleton interval and `Integer.MAX_VALUE` otherwise. `SumConstraint`, `LinearConstraint` (with a `Double` bound), `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `LexConstraint`, `CumulativeConstraint`, and `MaxConstraint` support `BoundedDomain` variables; `ConstraintSatisfactionProblem`'s build-time validation rejects any other constraint type referencing one with `IllegalArgumentException`. `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, and `AbsoluteDifferenceConstraint` also handle mixed discrete/interval pairs: they read the numeric range of the discrete side via `NumericBounds` to clip the interval variable's bounds, leaving the discrete side for AC3
- **`Assignment`** — immutable mapping of variables to values; validated against domains and constraints
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — hierarchical constraint interfaces; each checks `isSatisfiedBy(Assignment)`
- **`ConstraintSatisfactionProblem`** — aggregates variables, domains, constraints; analyzes graph structure (tree/cyclic, connected components, cutsets); `isFullyDetermined()` returns true when every variable's domain is a singleton
- **`SolverLimits`** — immutable configuration for node and time limits (`ofNodes`, `ofTime`, `of`, `unlimited`); also holds mutable runtime state (`AtomicReference<Statistics> limitHitStats`, excluded from `equals`/`hashCode`/`toString` via Lombok annotations). `markLimitReached(Statistics)` is called by search methods on first limit detection (CAS); `isLimitReached()` / `getLimitHitStatistics()` / `resetLimitReached()` are called by `BoundSolver.getSolution()`. `deadlineNanos()` returns `Long.MAX_VALUE` for unlimited; `isTimeLimitExceeded` uses subtraction (`nanoTime - deadline >= 0`) for overflow-safe comparison. Pass to `Solver.Factory.INSTANCE.createSolver(csp, limits)` or `createSolver(csp, objective, limits)`; the no-arg overloads delegate to these with `SolverLimits.unlimited()`
- **`LimitExceededException`** — unchecked exception thrown by `BoundSolver.getSolution()` (satisfaction chain only) when a node or time limit is exceeded; carries a `Statistics` snapshot captured at the point of detection. Distinguishes limit-hit from genuine UNSAT (`Optional.empty()`). `getSolutions()` truncates the stream silently — callers who want partial results should consume the stream directly
- **`BoundSolver`** — public API returned by `Solver.Factory.createSolver(csp)` and `createSolver(csp, objective)`; wraps a built chain with the CSP already bound. `getSolutions()` and `getSolution()` are both abstract (no default). The satisfaction factory returns an anonymous class that resets `limits.resetLimitReached()` before each search, calls `getSolutions().findFirst()`, then throws `LimitExceededException` if `limits.isLimitReached()`. The optimization factory returns an anonymous class that overrides `getSolution()` to use `reduce((a,b) -> b)` — limits truncate the stream silently and the best partial result is returned

### Solver Chain (Decorator Pattern)

`Solver.Factory.INSTANCE` builds two distinct chains, each returning a `BoundSolver` with the CSP already bound:

**Satisfaction** (`createSolver(csp)`): `NodeConsistency → PropagationFixpoint(snap=true) → IndependentSubproblems → TreeDecomposition → CutsetConditioning → TreeSolver / DomWdegLubySearch`

**Optimization** (`createSolver(csp, objective)`): `NodeConsistency → PropagationFixpoint(snap=false) → BisectionConditioning (continuous only) → BranchAndBound`

Key decorators:

1. **`NodeConsistentSolver`** — prunes domains via node consistency
2. **`PropagationFixpointSolver`** — runs UnaryComparatorConstraint/BinaryComparatorConstraint/BinaryOffsetConstraint/AbsoluteDifferenceConstraint interval bounds clipping, AC3, AllDiff GAC (Régin 1994), SumConstraint bounds propagation, LinearConstraint bounds propagation, CountConstraint value propagation, InverseConstraint arc consistency, AmongConstraint value-set propagation, AtLeastNConstraint/AtMostNConstraint boolean forcing, CumulativeConstraint timetabling propagation, GlobalCardinalityConstraint value propagation, LexConstraint bounds propagation, MaxConstraint bounds propagation, and NaryTuplesConstraint table GAC in a combined fixpoint loop via `PROPAGATORS` list; each propagator can enable the others to make further reductions. Many highly-constrained problems (Zebra, Sudoku, MagicSquare) are solved entirely at this step. Adding a new propagator requires one line: `FixpointConsistency.of(MyConstraint.class)`. The `snap` field controls `BoundedDomain` handling: `true` (satisfaction chain) snaps non-singleton intervals to their midpoint giving one concrete solution; `false` (optimization chain) leaves intervals open for `BisectionConditioningSolver` downstream.
3. **`BisectionConditioningSolver`** — only in the optimization chain; handles `BoundedDomain` variables that remain non-singleton after propagation by recursively bisecting the widest interval at its midpoint, re-propagating `SumConstraint`/`LinearConstraint` bounds on each half, and snapping to the midpoint once the width falls within `epsilon` (`Solver.Factory.DEFAULT_BISECTION_EPSILON = 1e-3`). When `findWidestBounded` returns null (all bounded domains are singleton), checks `isFullyDetermined()`: if true returns `forcedSolution(csp)`; if false (discrete variables remain) delegates to the inner chain. Passes through entirely for discrete CSPs (no bounded domains). Streams feasible points filtered by improving objective.
4. **`IndependentSubproblemSolver`** — decomposes into independent subproblems and combines solutions (satisfaction chain only)
5. **`TreeDecompositionSolver`** — applies tree decomposition for near-tree problems; skipped when constraint graph minimum degree ≥ targetTreewidth (exact early exit)
6. **`CutsetConditioningSolver`** — handles cyclic graphs by conditioning on a cycle cutset
7. **`TreeSolver`** / **`DomWdegLubySearch`** — terminal solvers for tree-structured or general CSPs respectively; `BranchAndBoundSolver` wraps `BacktrackingSearch` in the optimization chain

`SolverDecorator.getSolutions()` short-circuits immediately when any preprocessing step reduces all domains to singletons, returning the forced assignment without invoking downstream stages.

`DomWdegLubySearch` — the terminal solver in the satisfaction chain — combines two complementary techniques: **dom/wdeg variable ordering** (Boussemart et al. 2004) and **Luby restarts**. Each constraint starts with weight 1; when MAC inference causes a domain wipeout the weights of all active constraints on the failing variable are incremented. The selector picks `argmin(domainSize / weightedDegree)`, where weighted degree is the sum of weights of constraints involving the variable that also involve at least one other unassigned variable (variables with no active constraints get `MAX_VALUE` and are chosen last). `getSolutions()` returns a complete lazy stream of all solutions with dom/wdeg ordering and weight accumulation; when limits are hit in `searchStream`, `limits.markLimitReached(stats)` is called and `Stream.empty()` is returned (silent truncation). `getSolution()` additionally applies Luby restarts — the failure budget per restart follows the sequence 1, 1, 2, 1, 1, 2, 4, … (multiplied by `DEFAULT_LUBY_UNIT = 100`) and weights are preserved across restarts; when `SolverLimits` are hit in `searchOne`, a cumulative `Statistics` snapshot (total nodes across all restarts via `long[] totalNodes`) is captured via `limits.markLimitReached(stats)` and `LimitsExceeded` (internal pre-allocated sentinel) is thrown, which `getSolution()` catches and re-throws as `LimitExceededException`; `Optional.empty()` is returned only when the problem is genuinely UNSAT. `BacktrackingSearch` (with `MinimumRemainingValuesSelector`) is retained for the optimization chain inside `BranchAndBoundSolver`.

`ArcConsistentSolver`, `AllDiffConsistentSolver`, and `CumulativeConsistentSolver` have been deleted — their functionality is covered by `AC3.INSTANCE` and `FixpointConsistency.of(...)` entries in `PROPAGATORS`.

### Local Search Chain

`LocalSolver.Factory.INSTANCE.createLocalSolver(maxAttempts, maxSteps, factory)` builds:

```
NodeConsistency → UnaryComparatorBounds → BinaryComparatorBounds → OffsetBounds → AC3 → SumBounds → LinearBounds → CountValue → InverseArc → AmongValue → AtLeastN/AtMostN → CumulativeTimetable → GlobalCardinalityValue → LexBounds → TuplesGAC → IndependentSubproblems → MinConflicts
```

Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory`.

`MinConflictsSolver` and `WalkSATSolver` each run all `maxAttempts` restarts in parallel via `IntStream.parallel()`: the satisfaction path returns the first solution found (unordered); the optimization path runs all attempts in parallel and returns the true global minimum (MinConflicts only — WalkSAT does not support optimization). `IndependentSubproblemLocalSolver` uses `parallelStream()` so disjoint subproblems are also solved concurrently.

`LocalSolver.Factory.INSTANCE` automatically routes to `WalkSATSolver` for the satisfaction overload when all variable domains are boolean (`BooleanDomain` or `DiscreteDomain` containing only booleans) AND the CSP contains no `ExactlyOneConstraint` or `AtLeastNConstraint` (which require two-step moves that WalkSAT handles poorly). Falls back to `MinConflictsSolver` otherwise. The objective overload routes to `LargeNeighborhoodSolver` when the reduced CSP contains any `ExactlyOneConstraint` (LNS's destroy-repair moves over exactly-one slots outperform MinConflicts for these problems); falls back to `MinConflictsSolver` otherwise.

`LargeNeighborhoodSolver` — destroy-repair local search for boolean CSPs with `ExactlyOneConstraint`s. Each step picks `slotsPerStep` (default 2) random exactly-one slots, enumerates all valid refill combinations (exactly one variable true per slot), and accepts the combination with the fewest violations (ties broken by objective). Runs all `maxAttempts` restarts in parallel; supports both satisfaction and optimization. Wrapped in `IndependentSubproblemLocalSolver` like other terminal solvers.

The preprocessing chain before `MinConflicts` runs all `Propagatable` constraint types (via `PREPROCESSORS` list in `LocalSolver.Factory`) in the same order as `PropagationFixpointSolver.PROPAGATORS` except without the outer fixpoint loop and without AllDiff GAC. Includes `AtLeastNConstraint` and `AtMostNConstraint` propagation — particularly useful for local search where these boolean constraints have no binary decomposition to rely on. This detects infeasibility early (avoiding wasted attempts) and prunes domains to improve initial assignment quality. AllDiff GAC is intentionally excluded — it is expensive and repair-based search does not benefit from that level of arc consistency.

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
csp.maxConstraint(Set.of(v1, v2, v3), Operator.LEQ, 10)
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
- **`ConstraintConsistency` interface** — `@FunctionalInterface` in `consistency` package; the common contract for all propagation/consistency passes: `apply(ConstraintSatisfactionProblem) → Optional<ConstraintSatisfactionProblem>`. Implemented by `AC3`, `NodeConsistency`, and any `FixpointConsistency.of(...)` instance. `PropagationFixpointSolver` iterates a static `PROPAGATORS` list; `LocalSolver.Factory` iterates a static `PREPROCESSORS` list — adding a new propagator to either chain is a one-line `FixpointConsistency.of(MyConstraint.class)` entry.
- **`FixpointConsistency`** — concrete final class in `consistency.fixpoint` package; implements `ConstraintConsistency` for the common pattern of filtering constraints by type and running them to fixpoint. Created via `FixpointConsistency.of(XxxConstraint.class)`; logs under `FixpointConsistency` using the constraint type's simple name in the message. Replaces the deleted per-type consistency classes and the deleted `ConsistencyFixpoint` utility class.
- **`Propagatable` interface** — in `consistency` package; constraints that support domain propagation implement `propagate(Map<Variable<?>, Domain<?>> domains) → Optional<Map<Variable<?>, Domain<?>>>`. `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `AllDiffConstraint`, `SumConstraint`, `LinearConstraint`, `CountConstraint`, `InverseConstraint`, `AmongConstraint`, `AtLeastNConstraint`, `AtMostNConstraint`, `CumulativeConstraint`, `GlobalCardinalityConstraint`, `LexConstraint`, `MaxConstraint`, and `NaryTuplesConstraint` implement it. Each is registered in the solver chains via `FixpointConsistency.of(XxxConstraint.class)`. `CountConstraint` and `AmongConstraint` propagation classifies variables as definite/possible/impossible, then prunes domains when the count quota is met (EQ/LEQ) or must be reached (EQ/GEQ). `AtLeastNConstraint` forces possibly-true boolean variables to true when exactly enough remain; `AtMostNConstraint` forces them to false when the quota is filled. `InverseConstraint` propagation runs two passes of pairwise arc consistency between the `f` and `invf` arrays. `GlobalCardinalityConstraint` propagation applies the same definite/possible classification per tracked value, in turn: removing the value from possible domains once its cardinality is met, or forcing remaining possibles to that value once the cardinality can only just be reached. `LexConstraint` propagation scans pairs left to right for the first position not already forced equal (singleton domains with the same `singleValue()`), then clips that position's lesser upper bound to the greater's max and the greater's lower bound to the lesser's min (using `withBounds` for `BoundedDomain`, value deletion for `DiscreteDomain`); at the final position a strict operator (`LT`/`GT`) applies `<` rather than `<=`; `GEQ`/`GT` are handled by swapping pair roles; if every position is forced equal, the constraint reduces to `0 <op> 0`. `MaxConstraint` propagation runs two passes via `NumericBounds`: an upper-bound pass (EQ/LEQ/LT) clips every variable's maximum to the bound — infeasible when the maximum of all domain minimums already exceeds the bound; a lower-bound pass (EQ/GEQ/GT) checks that at least one variable can reach the bound — infeasible when none can, and when exactly one can its minimum is raised to the bound; for `EQ` both passes combine, and if only the sole reaching variable's discrete domain has no value equal to the bound the forced narrowing produces an empty domain signalling infeasibility. `NaryTuplesConstraint` propagation is full table GAC: a tuple is "live" if every value it assigns is still present in the corresponding domain; if no tuple remains live the constraint is infeasible, otherwise each variable's domain is pruned to the values used by at least one live tuple. `SumConstraint` and `LinearConstraint` dispatch to `propagateDouble` instead of the integer `propagateInt` path when their bound is a `Double`, using interval arithmetic (no `floorDiv`/`ceilDiv` rounding) via the shared `NumericBounds` helper (`constraints` package), which extracts `min`/`max` from either a `BoundedDomain` or an enumerable `Domain<N>` and narrows via `BoundedDomain.withBounds` or value deletion respectively — this is what allows `IntervalDomain` variables to be solved by propagation alone. `UnaryComparatorConstraint` clips a single interval variable's bounds via operator (GEQ/GT raises the floor, LEQ/LT lowers the ceiling, EQ does both). `BinaryComparatorConstraint` and `BinaryOffsetConstraint` clip both variables' bounds relative to each other (offset-shifted for the latter); both skip non-bounded variables and use `NumericBounds.min/max` to read the numeric range of any discrete partner. `AbsoluteDifferenceConstraint` propagates `|x−y| op d`: for `LEQ`/`LT` it clips both bounds symmetrically (`x ∈ [y.min−d, y.max+d]` and vice versa); for `EQ` the same narrowing applies plus an infeasibility check when the maximum achievable distance falls below `d`; for `GEQ`/`GT` only infeasibility is detected (the feasible region is non-convex); `NEQ` is skipped. `CumulativeConstraint` propagation uses an event-based step-function timetabling algorithm: it identifies compulsory parts, builds an exclusive resource profile per task, finds overloaded intervals, and tightens each start domain by advancing EST past and retreating LST before forbidden start windows; start variables may be `Variable<Double>` backed by `IntervalDomain` (continuous scheduling) or `Variable<Integer>` backed by `IntRangeDomain` (integer scheduling) — both use `NumericBounds.min/max` to read bounds and dispatch the narrowed output to `IntervalDomain.of` or `IntRangeDomain.of` respectively; durations, resources, and limit are `double` internally regardless of domain type.
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
- `ParkrunSchedulingTest` / `TimetableSchedulingBinaryAssignmentTest` — real-world scheduling; `ParkrunSchedulingTest` exercises the LNS optimization path via `ExactlyOneConstraint`
- `LargeNeighborhoodSolverTest` — unit tests for `LargeNeighborhoodSolver` (satisfaction and optimization paths)
- `DomWdegLubySearchTest` — unit tests for `DomWdegLubySearch`: Luby sequence correctness, dom/wdeg solving, Luby restart budget exhaustion, backtracking through non-propagated constraints, builder validation, and `SolverLimits` enforcement (node/time limits truncate `getSolutions()`; `getSolution()` throws `LimitExceededException`; cumulative node count across Luby restarts)
- `BacktrackingSearchTest` — unit tests for `BacktrackingSearch` (optimization chain terminal solver): backtracking via non-propagated biPredicate constraints and UNSAT inference-failure path
- `SolverLimitsTest` — unit tests for `SolverLimits`: factory methods, node/time limit checking, `markLimitReached`/`isLimitReached`/`getLimitHitStatistics`/`resetLimitReached` lifecycle, CAS idempotency (first-wins)
- `BoundSolverLimitsTest` — integration tests for factory `BoundSolver.getSolution()` limit behavior; uses 8-queens CSP (min constraint-graph degree = 7 ≥ targetTreewidth so `TreeDecompositionSolver` early-exits and `DomWdegLubySearch.getSolutions()` is the terminal path where limits apply); covers: `getSolution()` throws on node/time limit, `getSolution()` returns empty for genuine UNSAT, `getSolutions()` truncates silently on node limit, reset allows repeated limit detection
- `RealValuedConstraintTest` — `IntervalDomain` variables solved by bounds propagation; covers `sumConstraint`, `linearConstraint`, `comparatorConstraint` (unary and binary), `offsetConstraint`, `lexConstraint`, `cumulativeConstraint`, and `maxConstraint` over interval domains
- `ContinuousOptimizationTest` — continuous optimization via `createSolver(csp, objective).getSolution()` over `IntervalDomain` variables; bisection explores the feasible region down to `DEFAULT_BISECTION_EPSILON`
