# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.29.0</version>
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
mvn compile                                                                        # Compile sources
mvn test -Dorg.slf4j.simpleLogger.log.io.github.rcrida.jcsp=error                  # Run all tests
mvn test -Dtest=ClassName -Dorg.slf4j.simpleLogger.log.io.github.rcrida.jcsp=error # Run a single test class
mvn clean verify -Dorg.slf4j.simpleLogger.log.io.github.rcrida.jcsp=error          # Build with JaCoCo coverage report
```

Coverage report is generated at `target/site/jacoco/index.html`. **100% instruction and branch coverage is enforced** — the build fails if any code is not covered.

## Architecture Overview

This is a Constraint Satisfaction Problem (CSP) solver library implementing classic AI algorithms. The core flow is: define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.INSTANCE.createSolver(csp).getSolutions()` to get a lazy `Stream` of `Assignment` solutions.

### Core Abstractions

- **`Variable`** — immutable identifier; created via `Variable.Factory`
- **`Domain`** — base interface for all domains; defines `contains()`, `isEmpty()`, `size()`, and default `isSingleton()` (via `size() == 1`); `singleValue()` is abstract (discrete domains default it via `stream()`, bounded domains implement it directly)
- **`DiscreteDomain<T> extends Domain<T>`** — enumerable domains; adds `stream()`, `toList()`, and `toBuilder()` (with inner `Builder<T>` interface). Default `singleValue()` uses `stream()`. Code that needs to enumerate values should be typed to `DiscreteDomain`, not `Domain`
- **`SetDomain<T> extends DiscreteDomain<T>`** — intermediate interface for all set-backed discrete domains; declares `values() → Set<T>` and provides default implementations of all `DiscreteDomain` methods via that single accessor. Concrete implementors (`DomainObjectSet`, `IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `AssignedDomain`, `AssignmentDomain`) are all records that implement `SetDomain<T>` directly. Provides static helpers `domainEquals`/`domainHashCode` so cross-type equality works: two `SetDomain` instances with the same `values()` set are equal regardless of concrete record type. `DefaultBuilder` inner class delegates `build()` to `DomainObjectSet`. `IntRangeDomain` and `EnumDomain` store values in a `LinkedHashSet` (preserving ascending / declaration order) so `stream()` is deterministic and the solver's first-solution heuristic is stable.
- **`BoundedDomain<T extends Number & Comparable<T>>`** — `Domain` extension for non-enumerable continuous ranges; `getMin()`/`getMax()`/`withBounds(newMin, newMax)`. `IntervalDomain` is the sole implementation: a record with `double min, double max` components; `size()` returns `1` for a singleton interval and `Integer.MAX_VALUE` otherwise. `SumConstraint`, `LinearConstraint` (with a `Double` bound), `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `DivisionConstraint` (positive domains only), `LexConstraint`, `CumulativeConstraint`, `MaxConstraint`, `MinConstraint`, `ProductConstraint` (positive domains only), and `DiffnConstraint` (origin variables) support `BoundedDomain` variables; `ConstraintSatisfactionProblem`'s build-time validation rejects any other constraint type referencing one with `IllegalArgumentException`. `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, and `AbsoluteDifferenceConstraint` also handle mixed discrete/interval pairs: they read the numeric range of the discrete side via `NumericBounds` to clip the interval variable's bounds, leaving the discrete side for AC3
- **`Assignment`** — immutable mapping of variables to values; validated against domains and constraints
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — hierarchical constraint interfaces; each checks `isSatisfiedBy(Assignment)`
- **`ConstraintSatisfactionProblem`** — aggregates variables, domains, constraints; analyzes graph structure (tree/cyclic, connected components, cutsets); `isFullyDetermined()` returns true when every variable's domain is a singleton
- **`SolverLimits`** — immutable configuration for node and time limits (`ofNodes`, `ofTime`, `of`, `unlimited`); also holds mutable runtime state (`AtomicReference<Statistics> limitHitStats`, excluded from `equals`/`hashCode`/`toString` via Lombok annotations). `markLimitReached(Statistics)` is called by search methods on first limit detection (CAS); `isLimitReached()` / `getLimitHitStatistics()` / `resetLimitReached()` are called by `BoundSolver.getSolution()`. `deadlineNanos()` returns `Long.MAX_VALUE` for unlimited; `isTimeLimitExceeded` uses subtraction (`nanoTime - deadline >= 0`) for overflow-safe comparison. Pass to `Solver.Factory.INSTANCE.createSolver(csp, limits)` or `createSolver(csp, objective, limits)`; the no-arg overloads delegate to these with `SolverLimits.unlimited()`
- **`LimitExceededException`** — unchecked exception thrown by `BoundSolver.getSolution()` (satisfaction chain only) when a node or time limit is exceeded; carries a `Statistics` snapshot captured at the point of detection. Distinguishes limit-hit from genuine UNSAT (`Optional.empty()`). `getSolutions()` truncates the stream silently — callers who want partial results should consume the stream directly
- **`BoundSolver`** — public API returned by `Solver.Factory.createSolver(csp)` and `createSolver(csp, objective)`; wraps a built chain with the CSP already bound. `getSolutions()` and `getSolution()` are both abstract (no default). The satisfaction factory returns an anonymous class that resets `limits.resetLimitReached()` before each search, calls `getSolutions().findFirst()`, then throws `LimitExceededException` if `limits.isLimitReached()`. The optimization factory returns an anonymous class that overrides `getSolution()` to use `reduce((a,b) -> b)` — limits truncate the stream silently and the best partial result is returned
- **`PropagationResult`** — record (`consistency` package) returned by `Propagatable.propagateWithReasons`: `updatedDomains` (nullable) paired with a `reason` (`Map<Variable<?>, Object>`). `feasible(domains, reason)` / `infeasible(reason)` are the two static factories; `isInfeasible()` is `updatedDomains == null`. An empty `reason` map means the propagator hasn't implemented explanation and the caller falls back to the full assignment as the nogood. Powers `FixpointConsistency.explainConflict` and, transitively, `MacAndFixpointConflictExplainer` (see `DomWdegLubySearch` below)
- **`NogoodStore`** — `@Value` class (`assignments` package) that accumulates learned nogoods (partial variable→value maps known to fail) during backtracking search; `record(nogood)` and `isViolated(assignment)` are its public surface. See `DomWdegLubySearch` below for how it's wired into search

### Solver Chain (Decorator Pattern)

`Solver.Factory.INSTANCE` builds two distinct chains, each returning a `BoundSolver` with the CSP already bound:

**Satisfaction** (`createSolver(csp)`): `NodeConsistency → PropagationFixpoint(snap=true) → IndependentSubproblems → TreeDecomposition → CutsetConditioning → TreeSolver / DomWdegLubySearch`

**Optimization** (`createSolver(csp, objective)`): `NodeConsistency → PropagationFixpoint(snap=false) → BisectionConditioning (continuous only) → BranchAndBound`

Key decorators:

1. **`NodeConsistentSolver`** — prunes domains via node consistency
2. **`PropagationFixpointSolver`** — runs UnaryComparatorConstraint/BinaryComparatorConstraint/BinaryOffsetConstraint/AbsoluteDifferenceConstraint interval bounds clipping, AC3, AllDiff GAC (Régin 1994), SumConstraint bounds propagation, LinearConstraint bounds propagation, CountConstraint value propagation, InverseConstraint arc consistency, AmongConstraint value-set propagation, AtLeastNConstraint/AtMostNConstraint boolean forcing, CumulativeConstraint timetabling propagation, GlobalCardinalityConstraint value propagation, LexConstraint bounds propagation, MaxConstraint bounds propagation, MinConstraint bounds propagation, ProductConstraint bounds propagation (positive domains), DivisionConstraint bounds propagation (positive domains), NaryTuplesConstraint table GAC, CircuitConstraint Hamiltonian propagation, DiffnConstraint compulsory-part propagation, and RegularConstraint forward-backward DP propagation in a combined fixpoint loop via `PROPAGATORS` list; each propagator can enable the others to make further reductions. Many highly-constrained problems (Zebra, Sudoku, MagicSquare) are solved entirely at this step. Adding a new propagator requires one line: `FixpointConsistency.of(MyConstraint.class)`. The `snap` field controls `BoundedDomain` handling: `true` (satisfaction chain) snaps non-singleton intervals to their midpoint giving one concrete solution; `false` (optimization chain) leaves intervals open for `BisectionConditioningSolver` downstream.
3. **`BisectionConditioningSolver`** — only in the optimization chain; handles `BoundedDomain` variables that remain non-singleton after propagation by recursively bisecting the widest interval at its midpoint, re-propagating `SumConstraint`/`LinearConstraint` bounds on each half, and snapping to the midpoint once the width falls within `epsilon` (`Solver.Factory.DEFAULT_BISECTION_EPSILON = 1e-3`). When `findWidestBounded` returns null (all bounded domains are singleton), checks `isFullyDetermined()`: if true returns `forcedSolution(csp)`; if false (discrete variables remain) delegates to the inner chain. Passes through entirely for discrete CSPs (no bounded domains). Streams feasible points filtered by improving objective.
4. **`IndependentSubproblemSolver`** — decomposes into independent subproblems and combines solutions (satisfaction chain only)
5. **`TreeDecompositionSolver`** — applies tree decomposition for near-tree problems; skipped when constraint graph minimum degree ≥ targetTreewidth (exact early exit)
6. **`CutsetConditioningSolver`** — handles cyclic graphs by conditioning on a cycle cutset
7. **`TreeSolver`** / **`DomWdegLubySearch`** — terminal solvers for tree-structured or general CSPs respectively; `BranchAndBoundSolver` wraps `BacktrackingSearch` in the optimization chain

`SolverDecorator.getSolutions()` short-circuits immediately when any preprocessing step reduces all domains to singletons, returning the forced assignment without invoking downstream stages.

`DomWdegLubySearch` — the terminal solver in the satisfaction chain — combines two complementary techniques: **dom/wdeg variable ordering** (Boussemart et al. 2004) and **Luby restarts**. Each constraint starts with weight 1; when MAC inference causes a domain wipeout the weights of all active constraints on the failing variable are incremented. The selector picks `argmin(domainSize / weightedDegree)`, where weighted degree is the sum of weights of constraints involving the variable that also involve at least one other unassigned variable (variables with no active constraints get `MAX_VALUE` and are chosen last). It also carries a `NogoodStore` and a `ConflictExplainer` (both `@Builder` fields with defaults — a fresh `NogoodStore` and a no-op explainer that returns the assignment unchanged): on every domain wipeout the failing `next` assignment is explained via `conflictExplainer.explain(csp, variable, next)` and the resulting nogood is recorded in `nogoodStore` before backtracking; both `searchStream` and `searchOne` skip any candidate assignment that `nogoodStore.isViolated(next)` flags as subsuming a learned nogood, before the cheaper consistency/inference checks run. `Solver.Factory` wires a single `NogoodStore` per top-level search and passes `MacAndFixpointConflictExplainer.INSTANCE` as the explainer, so restarts share learned nogoods. `getSolutions()` returns a complete lazy stream of all solutions with dom/wdeg ordering and weight accumulation; when limits are hit in `searchStream`, `limits.markLimitReached(stats)` is called and `Stream.empty()` is returned (silent truncation). `getSolution()` additionally applies Luby restarts — the failure budget per restart follows the sequence 1, 1, 2, 1, 1, 2, 4, … (multiplied by `DEFAULT_LUBY_UNIT = 100`) and weights (and the shared `NogoodStore`) are preserved across restarts; when `SolverLimits` are hit in `searchOne`, a cumulative `Statistics` snapshot (total nodes across all restarts via `long[] totalNodes`) is captured via `limits.markLimitReached(stats)` and `LimitsExceeded` (internal pre-allocated sentinel) is thrown, which `getSolution()` catches and re-throws as `LimitExceededException`; `Optional.empty()` is returned only when the problem is genuinely UNSAT. `BacktrackingSearch` (with `MinimumRemainingValuesSelector`) is retained for the optimization chain inside `BranchAndBoundSolver`.

**Conflict explanation** — `ConflictExplainer` is a `@FunctionalInterface` (`solver` package) that turns a domain-wipeout assignment into a nogood: `explain(ConstraintSatisfactionProblem, Variable<?>, Assignment) → Map<Variable<?>, Object>`. `MacAndFixpointConflictExplainer.INSTANCE` (the production implementation) re-runs `MAC.INSTANCE.apply` with the failing assignment; if MAC itself wipes a domain it returns the assignment unchanged (no finer explanation is possible), otherwise it calls `PropagationFixpointSolver.explainConflict(postMac)`, which re-runs the `PROPAGATORS` fixpoint and, on the propagator that signals infeasibility, calls that propagator's `ConstraintConsistency.explainConflict` to get the reason — falling back to the assignment unchanged if no propagator offers one. `NogoodStore` (`assignments` package, `@Value`) accumulates these nogoods in a `CopyOnWriteArrayList` excluded from `equals`/`hashCode`/`toString` (same mutable-runtime-state-inside-`@Value` pattern as `SolverLimits`); `record(nogood)` defensively copies, `isViolated(assignment)` returns true when every variable-value pair of some recorded nogood is present in the assignment's values, and a single instance is shared across Luby restarts so learned nogoods compound.

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
csp.elementConstraint(index, result, array)          // result = array[index] (1-based); array is a fixed List<T>
csp.elementVariableConstraint(index, result, vars)   // result = vars[index] (1-based); vars is a List<Variable<T>>
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
csp.minConstraint(Set.of(v1, v2, v3), Operator.GEQ, 0)
csp.productConstraint(Set.of(v1, v2, v3), Operator.EQ, 24)  // v1*v2*v3==24; propagates for EQ/LEQ/GEQ when all domains have strictly positive mins
csp.divisionConstraint(dividend, divisor, Operator.EQ, 3)   // dividend/divisor==3; propagates for EQ/LEQ/GEQ when both domains have strictly positive mins
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
csp.circuitConstraint(List.of(s0, s1, s2))                         // Hamiltonian circuit; successors.get(i) = 1-indexed successor of node i+1
csp.diffnConstraint(xs, ys, widths, heights)                        // pairwise non-overlapping 2D rectangles; origin variables may be IntRangeDomain or IntervalDomain
csp.regularConstraint(sequence, automaton)                          // DFA-constrained sequence; values must spell a word accepted by the Automaton
```

**Reification**
```java
csp.reifyConstraint(b, constraint)    // b <-> constraint
csp.impliesConstraint(b, constraint)  // b -> constraint
```

### Key Conventions

- **Immutability**: `Assignment`, `Variable`, and constraint objects use Lombok `@Value`; `CSP` uses `@Builder`/`@Singular`. Constraint subclasses use `@SuperBuilder` + `@EqualsAndHashCode(callSuper = true)`. All domain classes are records: `IntervalDomain(double min, double max)`, `DomainObjectSet<T>(Set<T> values)`, `IntRangeDomain(Set<Integer> values)`, `EnumDomain<E>(Set<E> values)`, `BooleanDomain()`, `AssignedDomain(Object value)`, `AssignmentDomain(Set<Assignment> values)`.
- **Lombok**: `@Value`, `@Builder`, `@SuperBuilder`, `@Singular`, `@Slf4j` are used extensively — do not add manual boilerplate that Lombok already provides
- **Static factories**: All constraint classes have a static `of()` factory method; use these instead of `.builder()...build()` in production code
- **Null safety**: JSpecify `@NonNull`/`@Nullable` annotations throughout; `Optional` used for nullable returns
- **Logging**: All solvers/consistency algorithms use `@Slf4j` (SLF4J) for debug/info logging
- **Assertions**: Preconditions (e.g., equal list sizes) are checked with Java `assert` statements
- **`Operator` enum** — in `constraints` package; covers EQ, NEQ, LT, GT, LEQ, GEQ
- **`LogicOperator` enum** — in `constraints` package; covers AND, OR, XOR, NAND, NOR, XNOR
- **`ConstraintConsistency` interface** — `@FunctionalInterface` in `consistency` package; the common contract for all propagation/consistency passes: `apply(ConstraintSatisfactionProblem) → Optional<ConstraintSatisfactionProblem>`. Implemented by `AC3`, `NodeConsistency`, and any `FixpointConsistency.of(...)` instance. `PropagationFixpointSolver` iterates a static `PROPAGATORS` list; `LocalSolver.Factory` iterates a static `PREPROCESSORS` list — adding a new propagator to either chain is a one-line `FixpointConsistency.of(MyConstraint.class)` entry. Also declares a default `explainConflict(ConstraintSatisfactionProblem) → Optional<Map<Variable<?>, Object>>` returning `Optional.empty()`; `FixpointConsistency` overrides it (see below) to support `MacAndFixpointConflictExplainer`.
- **`FixpointConsistency`** — concrete final class in `consistency.fixpoint` package; implements `ConstraintConsistency` for the common pattern of filtering constraints by type and running them to fixpoint. Created via `FixpointConsistency.of(XxxConstraint.class)`; logs under `FixpointConsistency` using the constraint type's simple name in the message. Replaces the deleted per-type consistency classes and the deleted `ConsistencyFixpoint` utility class. Overrides `explainConflict`: filters the CSP's constraints to its `constraintType`, returns `Optional.empty()` immediately if none are present, then loops `propagateWithReasons` over them to a fixpoint, accumulating each step's `reason` into a `Map<Variable<?>, Object>`; the first propagator to report `isInfeasible()` contributes its reason to the accumulated map and that map is returned, otherwise (no infeasibility detected) returns `Optional.empty()`.
- **`Propagatable` interface** — in `consistency` package; constraints that support domain propagation implement `propagate(Map<Variable<?>, Domain<?>> domains) → Optional<Map<Variable<?>, Domain<?>>>`. Also declares a default `propagateWithReasons(Map<Variable<?>, Domain<?>> domains) → PropagationResult` that delegates to `propagate` and reports an empty reason map (`Map.of()`); constraints opt into richer explanations by overriding it directly — `BinaryComparatorConstraint` does so, attributing an infeasible bounds-narrowing to whichever side (left, right, or both) already holds a singleton domain, mapped to that domain's `singleValue()`; a side with an open (non-singleton) range is omitted since no single value can be blamed for it, and the reason is empty when neither side is singleton. `AtLeastNConstraint` and `AtMostNConstraint` also override it: on infeasibility, each attributes the conflict to every variable already forced to the "wrong" value — `AtLeastNConstraint` blames every variable forced `false` (a sufficient, not necessarily minimal, explanation for why the reachable true-count fell below `n`; empty when `n` exceeds the variable count while domains remain open, since nothing is forced false in that case), `AtMostNConstraint` blames every variable forced `true` (dual condition; structurally always non-empty for `n >= 0`, since exceeding `n` requires at least one forced-true variable). No other `Propagatable` implementer overrides `propagateWithReasons` yet (including `AllDiffConstraint`, whose Hall-set violations aren't reducible to a small set of variable-value pairs without further work) — they all fall back to the default's empty reason, and callers substitute the full assignment in that case. `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `DivisionConstraint`, `AllDiffConstraint`, `SumConstraint`, `LinearConstraint`, `CountConstraint`, `InverseConstraint`, `AmongConstraint`, `AtLeastNConstraint`, `AtMostNConstraint`, `CumulativeConstraint`, `GlobalCardinalityConstraint`, `LexConstraint`, `MaxConstraint`, `MinConstraint`, `NaryElementConstraint`, `NaryTuplesConstraint`, `ProductConstraint`, `CircuitConstraint`, `DiffnConstraint`, and `RegularConstraint` implement it. Each is registered in the solver chains via `FixpointConsistency.of(XxxConstraint.class)`. `CountConstraint` and `AmongConstraint` propagation classifies variables as definite/possible/impossible, then prunes domains when the count quota is met (EQ/LEQ) or must be reached (EQ/GEQ). `AtLeastNConstraint` forces possibly-true boolean variables to true when exactly enough remain; `AtMostNConstraint` forces them to false when the quota is filled. `InverseConstraint` propagation runs two passes of pairwise arc consistency between the `f` and `invf` arrays. `GlobalCardinalityConstraint` propagation applies the same definite/possible classification per tracked value, in turn: removing the value from possible domains once its cardinality is met, or forcing remaining possibles to that value once the cardinality can only just be reached. `LexConstraint` propagation scans pairs left to right for the first position not already forced equal (singleton domains with the same `singleValue()`), then clips that position's lesser upper bound to the greater's max and the greater's lower bound to the lesser's min (using `withBounds` for `BoundedDomain`, value deletion for `DiscreteDomain`); at the final position a strict operator (`LT`/`GT`) applies `<` rather than `<=`; `GEQ`/`GT` are handled by swapping pair roles; if every position is forced equal, the constraint reduces to `0 <op> 0`. `MaxConstraint` propagation runs two passes via `NumericBounds`: an upper-bound pass (EQ/LEQ/LT) clips every variable's maximum to the bound — infeasible when the maximum of all domain minimums already exceeds the bound; a lower-bound pass (EQ/GEQ/GT) checks that at least one variable can reach the bound — infeasible when none can, and when exactly one can its minimum is raised to the bound; for `EQ` both passes combine, and if only the sole reaching variable's discrete domain has no value equal to the bound the forced narrowing produces an empty domain signalling infeasibility. `MinConstraint` propagation is the dual: a lower-bound pass (EQ/GEQ/GT) raises every variable's minimum to the bound — infeasible when the minimum of all domain maximums falls below the bound; an upper-bound pass (EQ/LEQ/LT) checks that at least one variable can reach the bound — infeasible when none can, and when exactly one can its maximum is lowered to the bound; the same discrete-gap infeasibility applies for `EQ`. `NaryTuplesConstraint` propagation is full table GAC: a tuple is "live" if every value it assigns is still present in the corresponding domain; if no tuple remains live the constraint is infeasible, otherwise each variable's domain is pruned to the values used by at least one live tuple. `SumConstraint` and `LinearConstraint` dispatch to `propagateDouble` instead of the integer `propagateInt` path when their bound is a `Double`, using interval arithmetic (no `floorDiv`/`ceilDiv` rounding) via the shared `NumericBounds` helper (`constraints` package), which extracts `min`/`max` from either a `BoundedDomain` or an enumerable `Domain<N>` and narrows via `BoundedDomain.withBounds` or value deletion respectively — this is what allows `IntervalDomain` variables to be solved by propagation alone. `UnaryComparatorConstraint` clips a single interval variable's bounds via operator (GEQ/GT raises the floor, LEQ/LT lowers the ceiling, EQ does both). `BinaryComparatorConstraint` and `BinaryOffsetConstraint` clip both variables' bounds relative to each other (offset-shifted for the latter); both skip non-bounded variables and use `NumericBounds.min/max` to read the numeric range of any discrete partner. `AbsoluteDifferenceConstraint` propagates `|x−y| op d`: for `LEQ`/`LT` it clips both bounds symmetrically (`x ∈ [y.min−d, y.max+d]` and vice versa); for `EQ` the same narrowing applies plus an infeasibility check when the maximum achievable distance falls below `d`; for `GEQ`/`GT` only infeasibility is detected (the feasible region is non-convex); `NEQ` is skipped. `CumulativeConstraint` propagation uses an event-based step-function timetabling algorithm: it identifies compulsory parts, builds an exclusive resource profile per task, finds overloaded intervals, and tightens each start domain by advancing EST past and retreating LST before forbidden start windows; start variables may be `Variable<Double>` backed by `IntervalDomain` (continuous scheduling) or `Variable<Integer>` backed by `IntRangeDomain` (integer scheduling) — both use `NumericBounds.min/max` to read bounds and dispatch the narrowed output to `IntervalDomain.of` or `IntRangeDomain.of` respectively; durations, resources, and limit are `double` internally regardless of domain type.
- **`CircuitConstraint`** — enforces a single Hamiltonian circuit through all n nodes; `successors.get(i)` is the 1-indexed successor of node i+1; propagation removes self-loops (n > 1), applies singleton propagation (fixed successor pruned from all others), and eliminates sub-tours (follows assigned chains, prevents premature cycle closure). Implements both `Propagatable` and `BinaryDecomposable` (delegates to `AllDiffConstraint` for AC3 arc pairs since circuit implies all-different). Factory: `CircuitConstraint.of(List<Variable<Integer>> successors)`.
- **`DiffnConstraint`** — enforces pairwise non-overlap of axis-aligned rectangles; each rectangle i occupies `[x[i], x[i]+w[i]) × [y[i], y[i]+h[i])`; origin variables may be `IntRangeDomain` or `IntervalDomain`; propagation uses pairwise compulsory-part reasoning: if both rectangles' compulsory x-parts overlap then y-separation is enforced (and vice versa), returning infeasible if neither separation direction is possible. Added to `CONTINUOUS_COMPATIBLE_CONSTRAINTS`. Factory: `DiffnConstraint.of(xs, ys, widths, heights)`.
- **`Automaton<T>`** — record (`numStates`, `initialState`, `acceptingStates`, `transitions`) used by `RegularConstraint`; states are integers `0..numStates-1`; `transition(state, value)` returns -1 for missing (dead) transitions; compact constructor asserts all state indices are valid. Factory: `Automaton.of(numStates, initialState, acceptingStates, transitions)`.
- **`RegularConstraint`** — enforces that the sequence of values assigned to `sequence` is accepted by a `DFA`; propagation is full GAC via forward-backward DP: forward pass computes reachable states at each position, backward pass computes productive states (those that can still reach an accepting state), then values not on any accepting path are pruned. Does not have `@Value` (inherits `NaryConstraint.toString()`). Factory: `RegularConstraint.of(List<Variable<T>> sequence, Automaton<T> automaton)`.
- **`BinaryDecomposable` interface** — in `constraints` package; n-ary constraints that can be decomposed into an equivalent set of binary constraints implement `getAsBinaryConstraints() → Set<BinaryConstraint<?,?>>`. `AllDiffConstraint`, `AtMostOneConstraint` (and `ExactlyOneConstraint`), `IncreasingConstraint`, `DecreasingConstraint`, `ReifiedConstraint`, and `CircuitConstraint` implement it. `CircuitConstraint` delegates to `AllDiffConstraint.getAsBinaryConstraints()` since circuit implies all-different. Used by `ConstraintGraph` to infer additional binary constraints for AC3, and by `ConstraintSatisfactionProblem`/`MinConflictsSolver` to identify non-decomposable n-ary constraints. `ReifiedConstraint` returns an empty set when its body is not a `UnaryConstraint`.

### Integration Tests

Classic CSP problems serve as end-to-end integration tests in `io.github.rcrida.jcsp.solver.examples`:
- `AustraliaMapColouringTest` — graph coloring; also demonstrates `countConstraint` and `globalCardinalityConstraint`
- `NQueensTest` — N-Queens placement; also demonstrates `increasingConstraint` for symmetry breaking
- `MagicSquareTest` — magic square; demonstrates `sumConstraint` and `lexConstraint` for symmetry breaking
- `SudokuTest` — Sudoku solving
- `CryptarithmeticTest` — alphametic puzzle solving
- `ZebraPuzzleTest` — Einstein's Zebra puzzle
- `TwoSumTest` — two-sum via `elementConstraint` (fixed array)
- `PermutationSquareTest` — involution puzzle: find all permutations of {1..4} satisfying p(p(i))=i, modelled via `elementVariableConstraint`; 10 solutions
- `KnapsackTest` — binary knapsack via `linearConstraint` (feasibility + optimisation)
- `MenuCombinationTest` — extensional constraints via `tuplesConstraint`
- `SprintSchedulingTest` — resource-constrained scheduling via `cumulativeConstraint`
- `ReificationTest` — soft constraints via `reifyConstraint` / `impliesConstraint`
- `ParkrunSchedulingTest` / `TimetableSchedulingBinaryAssignmentTest` / `TimetableSchedulingViaColouringTest` — real-world scheduling; `ParkrunSchedulingTest` exercises the LNS optimization path via `ExactlyOneConstraint`
- `TaskAssignmentInverseTest` — task-to-person assignment modelled via `inverseConstraint`
- `MealPlanningTest` — menu planning via `countConstraint`, `sumConstraint`, and `globalCardinalityConstraint`
- `RealValuedConstraintTest` — `IntervalDomain` variables solved by bounds propagation; covers `sumConstraint`, `linearConstraint`, `comparatorConstraint` (unary and binary), `offsetConstraint`, `lexConstraint`, `cumulativeConstraint`, `maxConstraint`, `minConstraint`, `productConstraint`, and `divisionConstraint` over interval domains
- `ContinuousOptimizationTest` — continuous optimization via `createSolver(csp, objective).getSolution()` over `IntervalDomain` variables; bisection explores the feasible region down to `DEFAULT_BISECTION_EPSILON`
- `PythagoreanTriplesTest` — enumerates Pythagorean triples via `productConstraint` and `sumConstraint`
- `TravelingSalesmanTest` — TSP modelled via `circuitConstraint` with optimization
- `RectanglePackingTest` — packs four rectangles into a 3×3 bounding box via `diffnConstraint`; 12 solutions
- `NurseSchedulingTest` — 5-day nurse shift schedule (Day/Night/Rest) with DFA-encoded rules (no work after Night, ≤2 consecutive work days) via `regularConstraint`; 79 solutions
