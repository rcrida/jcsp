# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Documentation policy** — three places, three different kinds of content, no duplication between them:
- **Class/method Javadoc**: mechanical per-class/per-method behavior (what a `propagate()` or `explainInfeasible()` implementation does, why a particular subset of variables is cited, which helpers are shared between methods). Stays colocated with the code it describes and is more likely to be kept in sync when that code changes.
- **This file**: cross-cutting architecture that spans multiple classes — the current shape of the system, not why it got that way. A quick-reference map, not a decision log.
- **`docs/adr/`**: historical "why" for decisions that are architecturally significant, hard to reverse, or involved a real rejected alternative a future maintainer would otherwise have to rediscover (design decisions, dead ends tried and reverted, regressions a specific approach caused). See `docs/adr/README.md` for the format and full criteria.

Before adding a paragraph here, check whether it's actually a Javadoc-shaped mechanical explanation (put it there instead) or an ADR-shaped decision-with-alternatives (put it there instead, and link to it from here). If it's neither — just the current structural fact — it belongs here, kept short.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.36.0</version>
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

Coverage report is generated at `target/site/jacoco/index.html`. **100% instruction and branch coverage is enforced** — the build fails if any code is not covered. When the build fails on coverage and you need to find which specific branch is missed, use `target/site/jacoco/jacoco.csv` (one row per class, `INSTRUCTION_MISSED`/`BRANCH_MISSED` columns) to find the offending class, then read that class's `target/site/jacoco/<package>/<Class>.java.html` report (look for `pc`/`nc` span classes) for the exact line — don't parse `jacoco.xml`, the CSV plus per-class HTML is faster to work with.

`mvn verify` also runs Javadoc generation with `failOnWarnings=true` — a broken `{@link}` reference fails the build, not just the `javadoc:jar` step.

## Architecture Overview

This is a Constraint Satisfaction Problem (CSP) solver library implementing classic AI algorithms. The core flow is: define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.INSTANCE.createSolver(csp).getSolutions()` to get a lazy `Stream` of `Assignment` solutions.

### Core Abstractions

- **`Variable`** — immutable identifier; created via `Variable.Factory`
- **`Domain`** — base interface for all domains; defines `contains()`, `isEmpty()`, `size()`, and default `isSingleton()` (via `size() == 1`); `singleValue()` is abstract (discrete domains default it via `stream()`, bounded domains implement it directly)
- **`DiscreteDomain<T> extends Domain<T>`** — enumerable domains; adds `stream()`, `toList()`, and `toBuilder()` (with inner `Builder<T>` interface). Code that needs to enumerate values should be typed to `DiscreteDomain`, not `Domain`
- **`SetDomain<T> extends DiscreteDomain<T>`** — intermediate interface for all set-backed discrete domains; declares `values() → Set<T>` and derives every other `DiscreteDomain` method from it by default. Concrete implementors (`DomainObjectSet`, `IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `AssignedDomain`, `AssignmentDomain`, `NumericDiscreteDomain`) are all records. See [ADR-0007](docs/adr/0007-record-based-domain-object-model.md) for the record-based object model and why shared behavior lives in default interface methods.
- **`NumericDomain<N extends Number> extends Domain<N>`** — shared `getMin()`/`getMax()`/`withBounds(double, double)` contract for the two domain kinds with meaningful numeric bounds: `BoundedDomain` (continuous) and `IntRangeDomain` (discrete). `io.github.rcrida.jcsp.constraints.NumericBounds`'s helpers dispatch via `instanceof NumericDomain`, falling back to a stream scan for non-numeric `DiscreteDomain`s (e.g. `DomainObjectSet<String>`). See [ADR-0007](docs/adr/0007-record-based-domain-object-model.md).
- **`BoundedDomain<T extends Number> extends NumericDomain<T>`** — adds `withBounds(double, double)` narrowed to return `BoundedDomain<T>`. `IntervalDomain` is the sole implementation (`double min, double max`; `size()` is `1` for a singleton, else `Integer.MAX_VALUE`). Constraint types supporting `BoundedDomain` variables are whitelisted in `ConstraintSatisfactionProblem` — see [ADR-0006](docs/adr/0006-whitelist-based-domain-constraint-compatibility.md) for the whitelist itself and why some constraints (`AllDiffConstraint`, `CountConstraint`, etc.) are deliberately excluded. Not every `instanceof BoundedDomain` check in the codebase is safe to broaden to `NumericDomain` — see [ADR-0007](docs/adr/0007-record-based-domain-object-model.md)'s Consequences for which checks specifically must stay narrow.
- **`SetBoundedDomain<E>`** — `Domain<Set<E>>` extension for set-CP variables: a "set interval" (`getLowerBound()`/`getUpperBound()` under subset ordering) plus an independent cardinality range (`getMinCardinality()`/`getMaxCardinality()`), rather than enumerating every candidate subset. `SetIntervalDomain` is the sole implementation, requiring a `Comparator<E>` component (not optional, to eliminate null-handling from every consumer). See [ADR-0004](docs/adr/0004-set-cp-as-a-parallel-stack.md) for why this is a sibling of `BoundedDomain` rather than a subtype, and for the domain-intrinsic tightening rule its compact constructor applies.
- **`Assignment`** — immutable mapping of variables to values; validated against domains and constraints
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — hierarchical constraint interfaces; each checks `isSatisfiedBy(Assignment)`
- **`ConstraintSatisfactionProblem`** — aggregates variables, domains, constraints; analyzes graph structure (tree/cyclic, connected components, cutsets); `isFullyDetermined()` returns true when every variable's domain is a singleton. Carries a `nogoods: Set<NogoodConstraint>` field separately from its structural `constraints`/`ConstraintGraph`; `withNogoods(...)` reuses the existing `ConstraintGraph` untouched, since a `NogoodConstraint` never contributes to neighbours, binary decomposition, or cycle/connectivity analysis. `getConstraints()` returns a cached flat union of structural constraints and `nogoods` (invalidated only when the `nogoods` reference actually changes). `nogoods` and that union are excluded from `equals`/`hashCode` (learned nogoods are search knowledge, not part of the problem's identity); `constraintGraph` is included. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).
- **`SolverLimits`** — immutable configuration for node and time limits (`ofNodes`, `ofTime`, `of`, `unlimited`); also holds mutable runtime state (`AtomicReference<Statistics> limitHitStats`, excluded from `equals`/`hashCode`/`toString`). Set via `SolverConfig.builder().limits(...)`.
- **`SolverConfig`** — `@Value @Builder` bundling `createSolver`'s configuration knobs (`limits`, `nogoodLearningEnabled`, `statistics`). `createSolver(csp, SolverConfig)` / `createSolver(csp, objective, SolverConfig)` are the two true abstract methods on `Solver.Factory`; no-arg/objective-only overloads default to `SolverConfig.builder().build()`. `nogoodLearningEnabled` controls CDCL for both chains uniformly via the shared `nogoodLearningInference(SolverConfig)` helper. See [ADR-0005](docs/adr/0005-config-object-for-solver-configuration.md).
- **`LimitExceededException`** — unchecked exception thrown by `BoundSolver.getSolution()` (satisfaction chain only) when a node or time limit is exceeded; carries a `Statistics` snapshot. Distinguishes limit-hit from genuine UNSAT (`Optional.empty()`). `getSolutions()` truncates the stream silently instead.
- **`BoundSolver`** — public API returned by `Solver.Factory.createSolver(csp)`/`createSolver(csp, objective)`; wraps a built chain with the CSP already bound. `getSolutions()`/`getSolution()` are both abstract.
- **`PropagationResult`** — record (`consistency` package) returned by `Propagatable.propagateWithReasons`: `@Nullable updatedDomains` paired with a `@Nullable NogoodConstraint reason`. `feasible(domains, reason)`/`infeasible(reason)` are the two static factories. A `null` reason means the propagator hasn't implemented explanation; the caller falls back to the full assignment as the nogood.
- **`NogoodStore`** — accumulates learned nogoods during search as actual `NogoodConstraint`s, capped at `20 * variableCount` (floored at 50), evicting largest-arity first. `apply(csp)` returns `csp.withNogoods(...)`, a cached no-op when nothing new has been recorded. Shared across Luby restarts and (for `BranchAndBoundSolver`) across a whole optimization search. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md) for why nogoods are modelled as constraints rather than a search-side assignment map.

### Solver Chain (Decorator Pattern)

`Solver.Factory.INSTANCE` builds two distinct chains, each returning a `BoundSolver` with the CSP already bound. See [ADR-0001](docs/adr/0001-two-chain-decorator-solver-architecture.md) for why the chains are split this way rather than one solver branching internally.

**Satisfaction** (`createSolver(csp)`): `NodeConsistency → PropagationFixpoint(snap=true) → SetBranching (set variables only) → IndependentSubproblems → TreeDecomposition → CutsetConditioning → TreeSolver / DomWdegLubySearch`

**Optimization** (`createSolver(csp, objective)`): `NodeConsistency → PropagationFixpoint(snap=false) → BisectionConditioning (continuous only) → SetBranching (set variables only) → BranchAndBound`

Key decorators:

1. **`NodeConsistentSolver`** — prunes domains via node consistency
2. **`PropagationFixpointSolver`** — runs every registered propagator (interval bounds clipping, AC3, AllDiff GAC, sum/linear bounds, counting/global-cardinality, cumulative timetabling, table/regular/circuit constraints, ordering, reification, and the full set-CP list) to a combined fixpoint via a static `PROPAGATORS` list; each propagator can enable others to make further reductions. Many highly-constrained problems (Zebra, Sudoku, MagicSquare) are solved entirely at this step. Adding a new propagator is a one-line `FixpointConsistency.of(MyConstraint.class)` entry. The `snap` field controls `BoundedDomain` handling: `true` (satisfaction) snaps non-singleton intervals to their midpoint for one concrete solution; `false` (optimization) leaves intervals open for `BisectionConditioningSolver`.
3. **`BisectionConditioningSolver`** — optimization chain only; handles `BoundedDomain` variables remaining non-singleton after propagation by recursively bisecting the widest interval, re-propagating sum/linear bounds on each half, and snapping once width falls within `epsilon` (`Solver.Factory.DEFAULT_BISECTION_EPSILON = 1e-3`). Passes through entirely for discrete CSPs.
4. **`SetBranchingSolver`** — handles `SetBoundedDomain` variables remaining non-singleton after propagation, in *both* chains: picks the most-undetermined set variable, picks one undetermined element via the domain's own `getComparator()`, and explores "force it in"/"exclude it" as separate branches with real backtracking, re-propagating the full `PROPAGATORS` fixpoint after each. See [ADR-0004](docs/adr/0004-set-cp-as-a-parallel-stack.md) for why set variables need real branching in both chains rather than a single-shot snap the way continuous variables get.
5. **`IndependentSubproblemSolver`** — decomposes into independent subproblems and combines solutions (satisfaction chain only)
6. **`TreeDecompositionSolver`** — applies tree decomposition for near-tree problems; skipped when constraint graph minimum degree ≥ targetTreewidth
7. **`CutsetConditioningSolver`** — handles cyclic graphs by conditioning on a cycle cutset
8. **`TreeSolver`** / **`DomWdegLubySearch`** — terminal solvers for tree-structured or general CSPs respectively. `BranchAndBoundSolver` implements its own self-contained recursive search directly rather than delegating to `BacktrackingSearch`, since incumbent-based pruning needs to interleave with the recursion itself — see [ADR-0001](docs/adr/0001-two-chain-decorator-solver-architecture.md).

`SolverDecorator.getSolutions()` short-circuits immediately when any preprocessing step reduces all domains to singletons.

`DomWdegLubySearch` — the satisfaction chain's terminal solver — combines **dom/wdeg variable ordering** (Boussemart et al. 2004) with **Luby restarts**. Each constraint starts with weight 1; a domain wipeout increments the weights of active constraints on the failing variable. The selector picks `argmin(domainSize / weightedDegree)`. It carries a shared `NogoodStore` and an `Inference`, folding every nogood learned so far into the CSP checked/propagated against for each candidate value (see [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md)). `getSolutions()` returns a complete lazy stream; `getSolution()` additionally applies Luby restarts (failure budget sequence 1, 1, 2, 1, 1, 2, 4, … × `DEFAULT_LUBY_UNIT = 100`), preserving weights and the shared `NogoodStore` across restarts. `BacktrackingSearch` is a standalone, independently-tested generic implementation not wired into any production chain.

`BranchAndBoundSolver` mirrors the same `NogoodStore`/CDCL wiring in its own recursive search (a separate implementation, since its recursive shape doesn't fit `DomWdegLubySearch`'s stream-based one) — orthogonal to its own incumbent-bound pruning (`objective(partial) >= incumbent`): a nogood records a permanent constraint violation, the bound cut records cost dominance relative to the *current* incumbent, and the two prunings compose freely. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).

`NogoodConstraint` (`constraints.nary` package) is an interface (`extends Constraint, Propagatable`) modelling a learned nogood as an actual constraint that joins the same propagation fixpoint as every other constraint. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md) for why, and for the `Inference#applyWithReason`-based conflict-explanation mechanism. Registered once, for the whole interface, in `PropagationFixpointSolver.PROPAGATORS` via `NogoodFixpointConsistency.INSTANCE`. Three implementations exist:

- **`GroundNogoodConstraint`** — forbids one specific ground value per variable (`OR(x1 != v1, ..., xk != vk)`). See the class's own Javadoc for the classify/unit-propagate algorithm and its `explainInfeasible`/`fromReason` soundness argument.
- **`RangeNogoodConstraint`** — generalises the above to forbid a whole numeric range per variable, reusing `IntervalDomain` as the forbidden-region representation. Its `fromCurrentBounds` factory has a gaplessness gate (`isSafeToCiteAsRange`) that's load-bearing, not defensive — see the class's own Javadoc for the regression this fixed and why citing a gapped discrete domain's bounding interval is unsound.
- **`SetBoundsNogoodConstraint`** — the set-CP analogue, forbidding a whole `SetBoundedDomain` region per variable via sound-but-not-always-tight sufficient conditions (subset lattices can't be described by an exact interval the way numeric ranges can). See the class's own Javadoc.

Every implementation is registered in `ConstraintSatisfactionProblem` by concrete class, not the `NogoodConstraint` interface, per [ADR-0006](docs/adr/0006-whitelist-based-domain-constraint-compatibility.md). There is deliberately no separate "nogood prunes" statistic — see [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).

`ArcConsistentSolver`, `AllDiffConsistentSolver`, and `CumulativeConsistentSolver` have been deleted — their functionality is covered by `AC3.INSTANCE` and `FixpointConsistency.of(...)` entries in `PROPAGATORS`.

### Local Search Chain

`LocalSolver.Factory.INSTANCE.createLocalSolver(maxAttempts, maxSteps, factory)` builds:

```
NodeConsistency → UnaryComparatorBounds → BinaryComparatorBounds → OffsetBounds → AC3 → SumBounds → LinearBounds → CountValue → InverseArc → AmongValue → AtLeastN/AtMostN → CumulativeTimetable → GlobalCardinalityValue → LexBounds → TuplesGAC → IndependentSubproblems → MinConflicts
```

Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory`. `AllDiffConstraint`'s GAC is deliberately excluded from this preprocessing chain despite being in `PropagationFixpointSolver.PROPAGATORS` — see `LocalSolver.Factory.PREPROCESSORS`'s own Javadoc for why (repair-based search doesn't recoup Régin's matching cost the way a one-shot pass does).

`MinConflictsSolver`, `TabuSearchSolver`, and `WalkSATSolver` each run all `maxAttempts` restarts in parallel: satisfaction returns the first solution found; optimization runs all attempts and returns the true global minimum (MinConflicts/TabuSearch only — WalkSAT doesn't support optimization). `IndependentSubproblemLocalSolver` solves disjoint subproblems concurrently too.

`LocalSolver.Factory.INSTANCE` routes to `WalkSATSolver` when every domain is boolean and the CSP has no `ExactlyOneConstraint`/`AtLeastNConstraint`; otherwise to `raced` (`RaceLocalSolver` running `MinConflictsSolver` against `TabuSearchSolver`, wrapped in `IndependentSubproblemLocalSolver`). The objective overload routes to `LargeNeighborhoodSolver` when the reduced CSP contains any `ExactlyOneConstraint`; otherwise to `raced`. See [ADR-0003](docs/adr/0003-race-competing-strategies-over-predictive-routing.md) for why this is a mix of structural routing and performance racing, not a single predictive router.

`TabuSearchSolver` — the same min-conflicts move selection as `MinConflictsSolver`, plus short-term memory (`tabuTenure`, default 10 steps) forbidding reverting a variable to the value it just held, unless doing so would strictly improve the best *total* conflict weight seen so far this attempt (the aspiration criterion must compare total assignment cost, not local per-move cost — see `feedback_aspiration_needs_global_cost` in project memory).

`LargeNeighborhoodSolver` — destroy-repair local search for boolean CSPs with `ExactlyOneConstraint`s: each step picks `slotsPerStep` (default 2) random exactly-one slots, enumerates all valid refill combinations, and accepts the combination with the fewest violations (ties broken by objective).

**Conflict-scoring completeness** — `LocalSearchSupport.conflictConstraints` (shared by `MinConflictsSolver`/`TabuSearchSolver`/`WalkSATSolver`) prefers a constraint's `BinaryDecomposable` decomposition over the constraint itself for per-pair scoring granularity, but keeps the original constraint alongside an *incomplete* decomposition rather than dropping it. See [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md) for the bug this fixes and the `isDecompositionComplete()` flag it depends on.

### Constraint Construction

`CSP.Builder` provides fluent helper methods. All binary constraint classes also have static `of()` factory methods.

**Unary**
```java
csp.equalsConstraint(v, value)
csp.notEqualsConstraint(v, value)
csp.predicateConstraint(v, predicate)
csp.comparatorConstraint(v, Operator.GEQ, value)   // v >= value (Number types)
csp.setMembershipConstraint(s, element)            // element in s (set variable — Variable<Set<E>>; reify for a boolean membership indicator)
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
csp.subsetConstraint(left, right)                    // left ⊆ right (set variables — Variable<Set<E>>)
csp.disjointConstraint(left, right)                  // left ∩ right = ∅ (set variables)
csp.intersectionCardinalityConstraint(left, right, Operator.LEQ, 1)  // |left ∩ right| <= 1 (set variables; only LEQ/LT propagate)
csp.partitionConstraint(parts, universe)             // parts jointly partition universe (set variables; universe is fixed data)
```

**N-ary**
```java
csp.allDiffConstraint(Set.of(v1, v2, v3))
csp.atMostOneConstraint(Set.of(b1, b2, b3))        // AC3 decomposition into BinaryLogicConstraint(NAND) plus its own counting propagation
csp.atMostNConstraint(Set.of(b1, b2, b3), n)
csp.atLeastNConstraint(Set.of(b1, b2, b3), n)      // prefer for local search
csp.atLeastNConstraintWithCounting(Set.of(b1, b2, b3), n)  // prefer for backtracking (carry-chain)
csp.exactlyOneConstraint(Set.of(b1, b2, b3))       // also forces the sole remaining candidate true once every other is excluded
csp.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)
csp.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, target)  // v1+v2+v3 == target (target is a variable, not a constant)
csp.maxConstraint(Set.of(v1, v2, v3), Operator.LEQ, 10)
csp.minConstraint(Set.of(v1, v2, v3), Operator.GEQ, 0)
csp.productConstraint(Set.of(v1, v2, v3), Operator.EQ, 24)  // v1*v2*v3==24; propagates for EQ/LEQ/GEQ when all domains have strictly positive mins
csp.divisionConstraint(dividend, divisor, Operator.EQ, 3)   // dividend/divisor==3; propagates for EQ/LEQ/GEQ when both domains have strictly positive mins
csp.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, 10)  // weighted sum
csp.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, target)  // 2*v1+3*v2 <= target (target is a variable, not a constant)
csp.countConstraint(Set.of(v1, v2, v3), value, Operator.EQ, 2)
csp.amongConstraint(Set.of(v1, v2, v3), Set.of(a, b), Operator.EQ, 2)  // count vars with value in {a,b}
csp.inverseConstraint(List.of(f1, f2, f3), List.of(g1, g2, g3))        // f[i]==j ↔ g[j-1]==i+1
csp.globalCardinalityConstraint(Set.of(v1, v2, v3), Map.of(a, 2, b, 1))
csp.nValueConstraint(Set.of(v1, v2, v3), count)     // count == number of distinct values taken by v1,v2,v3; count is a variable, so it can be minimized
csp.binPackingConstraint(bin, weights, capacities)  // sum(weights[i] : bin[i]==b) <= capacities[b] for every bin b; pair with nValueConstraint over `bin` to minimize bins used
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

- **Immutability**: `Assignment`, `Variable`, and constraint objects use Lombok `@Value`; `CSP` uses `@Builder`/`@Singular`. Constraint subclasses use `@SuperBuilder` + `@EqualsAndHashCode(callSuper = true)`. All domain classes are records: `IntervalDomain(double min, double max)`, `DomainObjectSet<T>(Set<T> values)`, `IntRangeDomain(Set<Integer> values, int min, int max)`, `EnumDomain<E>(Set<E> values)`, `BooleanDomain()`, `AssignedDomain(Object value)`, `AssignmentDomain(Set<Assignment> values)`, `SetIntervalDomain<E>(Set<E> lowerBound, Set<E> upperBound, int minCardinality, int maxCardinality, Comparator<E> comparator)`, `NumericDiscreteDomain<N extends Number>(Set<N> values)`.
- **Lombok**: `@Value`, `@Builder`, `@SuperBuilder`, `@Singular`, `@Slf4j` are used extensively — do not add manual boilerplate that Lombok already provides
- **Static factories**: All constraint classes have a static `of()` factory method; use these instead of `.builder()...build()` in production code
- **Null safety**: JSpecify `@NonNull`/`@Nullable` annotations throughout; `Optional` used for nullable returns
- **Logging**: All solvers/consistency algorithms use `@Slf4j` (SLF4J) for debug/info logging
- **Assertions**: Preconditions (e.g., equal list sizes) are checked with Java `assert` statements
- **Javadoc cross-references**: when a Javadoc comment references another class, method, or field, use `{@link}`/`{@linkplain}` rather than `{@code}`, even when that means adding an import or fully qualifying the name to make the reference resolvable. `{@code}` renders as plain text and silently produces no warning if the thing it names is renamed or deleted; `{@link}` is checked by the `javadoc` goal (`mvn javadoc:javadoc`, run as part of `mvn verify`, which fails the build on any warning) and turns that drift into a build failure. This applies to field names too — check a mentioned name against the enclosing class's own fields/record components first, not just its methods, before assuming it's unlinkable (e.g. a parameter named `values` on a class whose own field is also `values` is that field being discussed, so `{@link #values}`, not `{@code values}`). Only fall back to `{@code}` for things that aren't real linkable program elements (e.g. a variable name, a conceptual term, or a class from a different module that can't be imported without a cyclic/undesirable dependency).
- **`Operator` enum** — in `constraints` package; covers EQ, NEQ, LT, GT, LEQ, GEQ
- **`LogicOperator` enum** — in `constraints` package; covers AND, OR, XOR, NAND, NOR, XNOR
- **`ConstraintConsistency` interface** — `@FunctionalInterface` in `consistency` package; the common contract for all propagation/consistency passes: `apply(ConstraintSatisfactionProblem) → Optional<ConstraintSatisfactionProblem>`. Implemented by `AC3`, `NodeConsistency`, and any `FixpointConsistency.of(...)` instance. The real per-pass explanation mechanism is `applyWithReason(csp, changedSinceLastRun) → ConsistencyResult`, overridden by `FixpointConsistency`, `AC3`, and `NogoodFixpointConsistency` — see [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).
- **`FixpointConsistency`** — concrete final class in `consistency.fixpoint` package; implements `ConstraintConsistency` for the common pattern of filtering constraints by type and running them to fixpoint. Created via `FixpointConsistency.of(XxxConstraint.class)`.
- **`NogoodFixpointConsistency`** — sibling to `FixpointConsistency`, specialized for `NogoodConstraint`: reads `csp.getNogoods()` directly rather than the flat structural+nogood union, and can skip nogoods whose variables are all absent from a dirty-variable hint. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).
- **`Propagatable` interface** — in `consistency` package; constraints that support domain propagation implement `propagate(Map<Variable<?>, Domain<?>> domains) → Optional<Map<Variable<?>, Domain<?>>>`, plus default `propagateWithReasons`/`explainInfeasible` hooks for conflict explanation (see [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md)) and a default `isNecessarilySatisfied(domains) → boolean` for the mirror "definitely true" signal (`ReifiedConstraint` is the sole consumer). Two `static` helpers factor out the two recurring explanation shapes — `addIfSingleton` ("blame the singleton side" for binary constraints) and `allSingletonReason` ("fully collective" explanation, sound only when every cited variable is singleton); see each implementing class's own Javadoc for why its violation shape needs one or the other.
- Detailed per-constraint `propagate()`/`explainInfeasible()` algorithm walkthroughs live in each constraint class's own Javadoc, not here. `UnaryComparatorConstraint` is the one `Propagatable` constraint that deliberately never implements `explainInfeasible` — see its own Javadoc for why (its infeasibility path is unreachable once search begins).
- **`CircuitConstraint`** — implements both `Propagatable` and `BinaryDecomposable`. Factory: `CircuitConstraint.of(List<Variable<Integer>> successors)`.
- **`DiffnConstraint`** — implements `Propagatable`. Factory: `DiffnConstraint.of(xs, ys, widths, heights)`.
- **`NValueConstraint`** — `count == |distinct values among trackedVariables|`, with `count` a genuine decision variable so it can be handed to an optimization objective. Bounds-consistency propagation only (GAC-nvalue is NP-hard). See [ADR-0006](docs/adr/0006-whitelist-based-domain-constraint-compatibility.md) for why it's not `BoundedDomain`-whitelisted.
- **`BinPackingConstraint`** — `weights`/`capacities` are fixed data; only the `bin` assignment is a decision. Composes with `nValueConstraint` over the same `bin` variables for "minimise bins used" — see `Prob034WarehouseLocationTest`/`BinPackingConstraintTest`.
- **`SumVariableConstraint`/`LinearVariableConstraint`** — siblings of `SumBoundConstraint`/`LinearBoundConstraint` for when the right-hand side is a variable (`target`) rather than a fixed constant. Extend `NaryConstraint` directly rather than `UniformNaryConstraint<N>`, whose `isSatisfiedBy` is `final`. Propagation is fully generic over `N extends Number` via `NumericBounds#propagateWeightedSumVsTarget`, working in `double` since `N`'s runtime type isn't recoverable from a bare `Variable<N>`.
- **`IncreasingConstraint`/`DecreasingConstraint`** — implement `Propagatable` directly (via the `OrderingPropagation` helper), in addition to their pairwise `BinaryDecomposable` decomposition; generic over any `Comparable<T>`, not restricted to `Number`.
- **`SubsetConstraint`/`DisjointConstraint`/`IntersectionCardinalityConstraint`** (`constraints.binary` package) — the three set-CP binary constraints; chain directly on `SetBoundedDomain`'s self-typed narrowing methods. `IntersectionCardinalityConstraint` only propagates for `Operator.LEQ`/`LT`. See [ADR-0004](docs/adr/0004-set-cp-as-a-parallel-stack.md).
- **`PartitionConstraint`** (`constraints.nary` package) — requires a `Set<Variable<Set<E>>> parts` to jointly partition a fixed `Set<E> universe` (disjoint + full coverage). `isDecompositionComplete()` is `false` — see [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md).
- **`ReifiedConstraint`/`ImplicationConstraint`** — implement `Propagatable`, giving an n-ary body real propagation instead of relying solely on `Assignment#isConsistent`'s direct check. See [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md) for the class of gap this closes.
- **`SetMembershipConstraint`** (`constraints.unary` package) — `element ∈ variable` over a `SetBoundedDomain` variable; exists primarily to be reified, since `SubsetConstraint`/`DisjointConstraint` can only force hard, unconditional membership/exclusion.
- **`Automaton<T>`** — DFA record used by `RegularConstraint`. Factory: `Automaton.of(numStates, initialState, acceptingStates, transitions)`.
- **`RegularConstraint`** — implements `Propagatable`. Factory: `RegularConstraint.of(List<Variable<T>> sequence, Automaton<T> automaton)`.
- **`BinaryDecomposable` interface** — in `constraints` package; n-ary constraints that can be decomposed into an equivalent set of binary constraints implement `getAsBinaryConstraints() → Set<BinaryConstraint<?,?>>`. `AllDiffConstraint`, `AtMostOneConstraint`/`ExactlyOneConstraint`, `IncreasingConstraint`, `DecreasingConstraint`, `ReifiedConstraint`, and `CircuitConstraint` implement it. Also declares `isDecompositionComplete()` — see [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md).

### Integration Tests

Classic CSP problems serve as end-to-end integration tests in `io.github.rcrida.jcsp.solver.examples`:
- `AustraliaMapColouringTest` — graph coloring; also demonstrates `countConstraint` and `globalCardinalityConstraint`
- `Prob054NQueensTest` — N-Queens placement; also demonstrates `increasingConstraint` for symmetry breaking
- `Prob019MagicSquareTest` — magic square; demonstrates `sumConstraint` and `lexConstraint` for symmetry breaking
- `Prob057KillerSudokuTest` — Killer Sudoku: standard Sudoku plus a full partition of all 81 cells into sum/all-different "cages", no given digits; instance transcribed from CSPLib's reference model
- `CryptarithmeticTest` — alphametic puzzle solving
- `ZebraPuzzleTest` — Einstein's Zebra puzzle
- `TwoSumTest` — two-sum via `elementConstraint` (fixed array)
- `PermutationSquareTest` — involution puzzle: find all permutations of {1..4} satisfying p(p(i))=i, modelled via `elementVariableConstraint`; 10 solutions
- `Prob133KnapsackTest` — binary knapsack via `linearConstraint` (feasibility + optimisation)
- `Prob034WarehouseLocationTest` — Warehouse Location Problem: minimise `sum(supplyCost) + nValueConstraint(warehousesUsed) * fixedCost`; demonstrates that only a genuine distinct-value count (not `globalCardinalityConstraint`'s fixed per-value quotas) can express "minimise how many warehouses end up used"
- `Prob038SteelMillSlabDesignTest` — Steel Mill Slab Design: `binPackingConstraint` plus `ld[j]` as a real linked variable via `linearConstraint`'s target-variable overload; `predicateConstraint` for the at-most-2-colours-per-slab rule; order-identity and `decreasingConstraint` symmetry breaking
- `MenuCombinationTest` — extensional constraints via `tuplesConstraint`
- `SprintSchedulingTest` — resource-constrained scheduling via `cumulativeConstraint`
- `ReificationTest` — soft constraints via `reifyConstraint` / `impliesConstraint`
- `ParkrunSchedulingTest` / `TimetableSchedulingBinaryAssignmentTest` / `TimetableSchedulingViaColouringTest` — real-world scheduling; `ParkrunSchedulingTest` exercises the LNS optimization path via `ExactlyOneConstraint`
- `TaskAssignmentInverseTest` — task-to-person assignment modelled via `inverseConstraint`
- `MealPlanningTest` — menu planning via `countConstraint`, `sumConstraint`, and `globalCardinalityConstraint`
- `RealValuedConstraintTest` — `IntervalDomain` variables solved by bounds propagation; covers `sumConstraint`, `linearConstraint`, `comparatorConstraint`, `offsetConstraint`, `lexConstraint`, `cumulativeConstraint`, `maxConstraint`, `minConstraint`, `productConstraint`, and `divisionConstraint` over interval domains
- `ContinuousOptimizationTest` — continuous optimization via `createSolver(csp, objective).getSolution()` over `IntervalDomain` variables
- `PythagoreanTriplesTest` — enumerates Pythagorean triples via `productConstraint` and `sumConstraint`
- `Prob075ProductMatrixTspTest` — Product Matrix TSP via `circuitConstraint` with optimization
- `RectanglePackingTest` — packs four rectangles into a 3×3 bounding box via `diffnConstraint`; 12 solutions
- `NurseSchedulingTest` — 5-day nurse shift schedule with DFA-encoded rules via `regularConstraint`; 79 solutions
- `Prob006GolombRulerTest` — order-5 Golomb ruler; proves the known-optimal length 11 (OEIS A003022) by satisfiability at 11 vs. unsatisfiability at 10
- `Prob061JobShopSchedulingTest` — 2-job, 2-machine job-shop scheduling optimized for makespan; same-machine mutual exclusion modelled as `cumulativeConstraint` with capacity 1
- `Prob010SocialGolfersTest` — CSPLib's smallest published instance; modelled via `partitionConstraint` and `intersectionCardinalityConstraint`; 48 solutions
- `Prob028BalancedIncompleteBlockDesignTest` — CSPLib's smallest published instance, the Fano plane; modelled from CSPLib's reference boolean-incidence-matrix model, not the set-CP style used elsewhere
- `Prob044SteinerTripleSystemTest` — CSPLib's smallest published instance (`N = 7`, the Fano plane again, via the set-CP style); 30 solutions
- `WindokuTest` — Hyper Sudoku/Windoku (standard row/column/box `allDiffConstraint` plus four extra offset 3x3 "window" regions, no CSPLib entry so no external instance to transcribe); solution grid generated by a standalone backtracking filler and its 42 givens confirmed unique via this library's own solver

### Benchmarking

`io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark` (`src/test/java`, plain `main()` class, not run by surefire) measures `NogoodStore` overhead across three variants (`default`/`capped`/`disabled`) against four deliberately different CSP shapes (`golombRuler`, `randomBinaryCsp`, `quasigroupCompletion`, `pigeonhole`) under the same node budget, so wall-clock differences are attributable to nogood-store/CDCL cost rather than different search decisions. Run via `mvn test-compile` then `java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark`.

`pigeonhole`'s three variants come out statistically identical — a confirmed, documented architectural gap, not a benchmark artifact: neither `ExactlyOneConstraint` nor `AtMostOneConstraint` was registered as a propagator, so pigeonhole failures were only ever caught by `Assignment#isConsistent`'s direct check, bypassing nogood learning regardless of configuration. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md) and [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md) for the fix and the two reverted attempts that preceded it.

Each run prints `nodesExplored`/`backtracks`/`nogoodsLearned` alongside timing wherever available — availability depends on how the solve terminates via `DomWdegLubySearch#getSolution`: SAT and limit-exceeded completions expose real `Statistics`; genuine UNSAT does not (a real API gap, not a benchmark oversight).
