# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Documentation policy** — three places, three different kinds of content, no duplication between them:
- **Class/method Javadoc**: mechanical per-class/per-method behavior (what a `propagate()` or `explainInfeasible()` implementation does, why a particular subset of variables is cited, which helpers are shared between methods). Stays colocated with the code it describes.
- **This file**: cross-cutting architecture that spans multiple classes — the current shape of the system, not why it got that way. A quick-reference map, not a decision log. No measurements, instance names, or "added because…" narratives.
- **`docs/adr/`**: historical "why" for decisions that are architecturally significant, hard to reverse, or involved a real rejected alternative (design decisions, dead ends tried and reverted, measurements that justified a choice). See `docs/adr/README.md` for the format and criteria.

Before adding a paragraph here, check whether it's actually Javadoc-shaped (put it there) or ADR-shaped (put it there, and link to it from here). If it's neither — just a current structural fact — it belongs here, kept to a line or two.

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>3.1.0</version>
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

**100% instruction and branch coverage is enforced** — the build fails if any code is not covered. To find a missed branch, use `target/site/jacoco/jacoco.csv` (`INSTRUCTION_MISSED`/`BRANCH_MISSED` per class) to find the class, then its `target/site/jacoco/<package>/<Class>.java.html` (`pc`/`nc` span classes) for the line. Don't parse `jacoco.xml`.

`mvn verify` also runs Javadoc with `failOnWarnings=true` — a broken `{@link}` fails the build.

## Architecture Overview

A Constraint Satisfaction Problem solver library. Define a `ConstraintSatisfactionProblem` (variables + domains + constraints), then call `Solver.Factory.INSTANCE.createSolver(csp).getSolutions()` for a lazy `Stream` of `Assignment` solutions, or `createSolver(csp, objective)` for optimization.

### Domains

- **`Domain`** — base interface: `contains()`, `isEmpty()`, `size()`, `isSingleton()`, `singleValue()`.
- **`DiscreteDomain<T>`** — enumerable (`stream()`, `toList()`, `toBuilder()`). Code that enumerates values should be typed to this, not `Domain`. `of(T...)`/`builder()` go through the hand-written `DiscreteDomain.DiscreteDomainBuilder`, collapsing to `ObjectEmptyDomain`/`ObjectSingletonDomain`/`ObjectSetDomain`.
- **`DiscreteSetDomain<T>`** — set-backed discrete domains; derives everything from `values()`. Implementors: `ObjectSetDomain`, `IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `AssignmentDomain`, `NumericSetDomain`.
- **`NumericDomain<N>`** — `getMin()`/`getMax()`/`withBounds(double, double)`, shared by continuous and numeric discrete domains. `NumericBounds` helpers dispatch on it.
- **`NumericDiscreteDomain<N>`** — combinator of numeric + discrete (`IntRangeDomain`, `NumericSetDomain`, `NumericSingletonDomain`); `of(N...)` is the main numeric-discrete constructor, with its own `NumericDiscreteDomainBuilder` so narrowing stays numeric.
- **`BoundedDomain<T>`** — continuous; `IntervalDomain(double min, double max)` is the sole implementation (`size()` is `Integer.MAX_VALUE` unless singleton). Which constraints accept it is a whitelist in `ConstraintSatisfactionProblem` — see [ADR-0006](docs/adr/0006-whitelist-based-domain-constraint-compatibility.md). Not every `instanceof BoundedDomain` check can be broadened to `NumericDomain` — see [ADR-0007](docs/adr/0007-record-based-domain-object-model.md).
- **`SetBoundedDomain<E>`** — set-CP variables as a set interval plus cardinality range; `SetIntervalDomain` is the sole implementation (requires a `Comparator<E>`). See [ADR-0004](docs/adr/0004-set-cp-as-a-parallel-stack.md).

Every domain is a record except `ObjectEmptyDomain`/`NumericEmptyDomain` (final classes with a single shared instance). See [ADR-0007](docs/adr/0007-record-based-domain-object-model.md).

### Core Abstractions

- **`Variable`** — immutable identifier, created via `Variable.Factory`.
- **`Assignment`** — immutable variable→value mapping, validated against domains and constraints.
- **`Constraint`** / `UnaryConstraint` / `BinaryConstraint` / `NaryConstraint` — each checks `isSatisfiedBy(Assignment)`.
- **`ConstraintSatisfactionProblem`** — variables, domains, constraints and the `ConstraintGraph` (tree/cyclic, components, cutsets). Holds learned `nogoods` separately from structural constraints; `withNogoods(...)` reuses the graph, and nogoods are excluded from `equals`/`hashCode`. `getConstraints()` is a cached union of both; `getConstraintsTouching(variable)` is the per-variable equivalent. `getDeclaredDomains()` are the graph's original domains. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md).
- **`SolverConfig`** — `@Value @Builder` of `createSolver`'s knobs: `limits` (`SolverLimits`), `nogoodLearningEnabled`, `statistics`, `listener` (`SolverListener`), `cancellation` (`Cancellation`), `restartRandomization` (`RestartRandomization`), `variableSelectorFactory` (`AdaptiveVariableSelector.Factory`). Everything defaults to an inert value except two:
  - Nogood learning is **off** unless asked for; read it via `learningEnabled()`, never the raw getter ([ADR-0030](docs/adr/0030-nogood-learning-is-not-cdcl.md)).
  - `restartRandomization` defaults to a fresh random seed. Use `RestartRandomization.seeded(fixedSeed)` for reproducible benchmarks. `NONE` is only for isolating the mechanism's absence. `AC3`'s arc order is salted per JVM either way ([ADR-0015](docs/adr/0015-seeded-restart-tie-breaking-random-by-default.md)).

  See also [ADR-0005](docs/adr/0005-config-object-for-solver-configuration.md), [ADR-0010](docs/adr/0010-push-listener-for-solve-progress.md), [ADR-0011](docs/adr/0011-cancellation-token-for-main-chain-search.md) and [ADR-0038](docs/adr/0038-injectable-variable-selector-factory.md).
- **`LimitExceededException`** / **`SolverCancelledException`** — thrown only from the single-solution searches (`DomWdegLubySearch.getSolution()` and, for cancellation, `CutsetConditioningSolver.getSolution()`). Every `getSolutions()` stream truncates silently instead. See [ADR-0011](docs/adr/0011-cancellation-token-for-main-chain-search.md).
- **`Statistics`** — shared counters. `getRootSearchSpace()`/`getRemainingSearchSpace()` (via `SearchProgress`) replace the deprecated `getCurrentSearchSpace()` ([ADR-0033](docs/adr/0033-search-space-metrics-that-mean-something.md)).
- **`BoundSolver`** — the public API `createSolver` returns, with the CSP already bound.
- **`PropagationResult`** — `propagateWithReasons`'s result: updated domains plus an optional `NogoodConstraint` reason (`null` means the caller falls back to the full assignment).
- **`NogoodStore`** — accumulates learned `NogoodConstraint`s (capped, evicting largest arity first). It is shared across Luby restarts and across a whole B&B search.

### Solver Chain (Decorator Pattern)

`Solver.Factory.INSTANCE` builds two chains ([ADR-0001](docs/adr/0001-two-chain-decorator-solver-architecture.md)):

**Satisfaction** (`createSolver(csp)`): `NodeConsistency → PropagationFixpoint(snap=true) → SetBranching (set variables only) → IndependentSubproblems → TreeDecomposition → CutsetConditioning → TreeSolver / DomWdegLubySearch`

**Optimization** (`createSolver(csp, objective)`): `NodeConsistency → PropagationFixpoint(snap=false) → SetBranching (set variables only) → BranchAndBound`

`SolverDecorator.getSolutions()` short-circuits when preprocessing reduces every domain to a singleton.

- **`PropagationFixpointSolver`** — one-time preprocessing via `FixpointPropagation`. `snap=true` snaps leftover intervals to their midpoint; `snap=false` leaves them open for B&B.
- **`FixpointPropagation`** — runs a list of `ConstraintConsistency` propagators to a combined fixpoint using a **propagator worklist** ([ADR-0024](docs/adr/0024-propagator-worklist.md)). `PROPAGATORS` is the full catalog; add a propagator with one `FixpointConsistency.of(MyConstraint.class)` entry. `FULL` uses the whole catalog. `Factory.INSTANCE.forProblem(csp, learning)` filters it per CSP once per solve ([ADR-0012](docs/adr/0012-per-csp-propagator-filtering.md)), and that instance is threaded into preprocessing, per-node inference and `SetBranchingSolver`. It checks `Cancellation` between propagators.
- **`SetBranchingSolver`** — branches element-in / element-out on undetermined set variables in both chains, repropagating after each branch ([ADR-0004](docs/adr/0004-set-cp-as-a-parallel-stack.md)).
- **`IndependentSubproblemSolver`** / **`TreeDecompositionSolver`** / **`CutsetConditioningSolver`** — structural decomposition (satisfaction chain only).
- **`TreeSolver`** / **`DomWdegLubySearch`** — terminal solvers for tree-structured and general CSPs.
- **`BranchAndBoundSolver`** — the optimization terminal. It runs its own recursive search rather than delegating to `BacktrackingSearch`.
- **`BisectionConditioningSolver`** — not a chain decorator. B&B builds one per discrete-complete leaf to resolve continuous residuals. `getSolution()` returns the *first* improving point, not the best.
- `BacktrackingSearch` is a standalone, tested generic implementation not wired into any chain.

**`DomWdegLubySearch`** — dom/wdeg variable ordering plus Luby restarts (`DEFAULT_LUBY_UNIT = 100`). Constraint weights, the `NogoodStore` and a `PhaseMemory` survive across restarts. Each restart reseeds tie-breaking via `restartRandomization`.
- `PhaseMemory` puts the remembered value first. It is written only from the strictly deepest assignment seen ([ADR-0026](docs/adr/0026-solution-guided-phase-saving.md)).
- After `STAGNANT_RESTART_LIMIT` restarts with no new deepest assignment, the weights and the phase memory are both reset ([ADR-0032](docs/adr/0032-adaptive-weight-reset-on-stagnant-restarts.md), [ADR-0039](docs/adr/0039-discard-the-phase-memory-on-stagnation.md)).
- `getSolutions()` has no restarts and doesn't consult the phase memory.

**`BranchAndBoundSolver`**:
- Incumbent pruning, composed with the same `NogoodStore` wiring ([ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md)).
- A `PhaseMemory` recorded on each strictly improving solution ([ADR-0026](docs/adr/0026-solution-guided-phase-saving.md)).
- No restarts ([ADR-0028](docs/adr/0028-restart-on-solution-rejected-for-branch-and-bound.md)).
- Each new incumbent is also applied as a propagated `LinearBoundConstraint` objective cut ([ADR-0029](docs/adr/0029-objective-cut-as-a-propagated-constraint.md)).
- When the objective is a `LinearObjective` (`constant + Σ coefficient·variable`, detected via `instanceof`), an LP relaxation from `solver.lp`'s `LpModelBuilder`/`LpBound` (ojAlgo) serves three purposes: a per-node bound, most-fractional branching, and an exact fill of continuous variables once every discrete variable is pinned ([ADR-0009](docs/adr/0009-joint-continuous-discrete-optimization.md)). The LP includes an assignment relaxation for GCC-linked tables ([ADR-0020](docs/adr/0020-assignment-relaxation-for-gcc-linked-tables.md)) and reuses a copied model template across nodes ([ADR-0025](docs/adr/0025-lp-model-reuse-across-search-nodes.md)).

### Nogoods

`NogoodConstraint` (`constraints.nary`) models a learned nogood as a real `Propagatable` constraint in the fixpoint, registered once via `NogoodFixpointConsistency.INSTANCE`. Implementations: `GroundNogoodConstraint` (ground values), `ValueSetNogoodConstraint` (value sets), `RangeNogoodConstraint` (numeric ranges; only for gapless domains), `SetBoundsNogoodConstraint` (set-CP). Each is whitelisted by concrete class. `Statistics#nogoodRejections` counts nogood-caused rejections but doesn't change their handling. See [ADR-0002](docs/adr/0002-nogood-learning-as-first-class-constraints.md) and [ADR-0030](docs/adr/0030-nogood-learning-is-not-cdcl.md).

### Arc Consistency

`AC3` (`consistency.arc`) is the arc-consistency propagator used everywhere. That covers `PROPAGATORS`' entry, `MAC` (run per search node before the fixpoint), `TreeSolver` and local-search preprocessing. `AC3BitRm` is a tested alternative that is deliberately **not wired in** ([ADR-0022](docs/adr/0022-bitset-and-residue-arc-consistency-ac3bitrm.md)).

### Local Search Chain

`LocalSolver.Factory.INSTANCE.createLocalSolver(maxAttempts, maxSteps, factory, LocalSolverConfig)` builds:

```
NodeConsistency → UnaryComparatorBounds → BinaryComparatorBounds → OffsetBounds → AC3 → SumBounds → LinearBounds → CountValue → InverseArc → AmongValue → AtLeastN/AtMostN → CumulativeTimetable → GlobalCardinalityValue → LexBounds → TuplesGAC → IndependentSubproblems → MinConflicts
```

- Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory` or `FallbackAssignmentFactory`.
- `AllDiffConstraint` GAC is deliberately excluded from preprocessing (see `LocalSolver.Factory.PREPROCESSORS`).
- `LocalSolverConfig` carries a `LocalSolverListener`. The 3-arg overload is deprecated.
- Routing: all-boolean with no `ExactlyOne`/`AtLeastN` → `WalkSATSolver`. Objective with `ExactlyOneConstraint` → `LargeNeighborhoodSolver`. Otherwise `RaceLocalSolver` races `MinConflictsSolver` against `TabuSearchSolver`, wrapped in `IndependentSubproblemLocalSolver` ([ADR-0003](docs/adr/0003-race-competing-strategies-over-predictive-routing.md)).
- Leaf solvers run `maxAttempts` restarts in parallel.
- `LocalSearchSupport.conflictConstraints` keeps the original constraint alongside an incomplete binary decomposition ([ADR-0008](docs/adr/0008-decomposition-completeness-flag.md)).

### Constraint Construction

`CSP.Builder` provides fluent helper methods; every constraint class also has a static `of()` factory.

**Unary**
```java
csp.equalsConstraint(v, value)                     // v == value (value must be Comparable<T>)
csp.notEqualsConstraint(v, value)                  // v != value (value must be Comparable<T>)
csp.predicateConstraint(v, predicate)
csp.comparatorConstraint(v, Operator.GEQ, value)   // v >= value (any Comparable<T>)
csp.setMembershipConstraint(s, element)            // element in s (set variable)
```

**Binary**
```java
csp.equalsConstraint(v1, v2)
csp.notEqualsConstraint(v1, v2)
csp.notEqualsChainConstraint(List.of(v1, v2, v3))  // consecutive pairs differ
csp.offsetConstraint(v1, offset, Operator.EQ, v2)  // v1 + offset == v2
csp.comparatorConstraint(v1, Operator.LEQ, v2)     // any Comparable; NEQ is rejected -- use notEqualsConstraint
csp.logicConstraint(b1, LogicOperator.OR, b2)       // AND/OR/XOR/NAND/NOR/XNOR
csp.elementConstraint(index, result, array)          // result = array[index] (1-based, fixed List<T>)
csp.elementVariableConstraint(index, result, vars)   // result = vars[index] (1-based, List<Variable<T>>)
csp.biPredicateConstraint(v1, v2, predicate)
csp.subsetConstraint(left, right)                    // left ⊆ right (set variables)
csp.disjointConstraint(left, right)                  // left ∩ right = ∅ (set variables)
csp.intersectionCardinalityConstraint(left, right, Operator.LEQ, 1)  // |left ∩ right| <= 1 (only LEQ/LT propagate)
csp.partitionConstraint(parts, universe)             // parts partition a fixed universe (set variables)
```

**N-ary**
```java
csp.allDiffConstraint(Set.of(v1, v2, v3))
csp.atMostOneConstraint(Set.of(b1, b2, b3))
csp.atMostNConstraint(Set.of(b1, b2, b3), n)
csp.atLeastNConstraint(Set.of(b1, b2, b3), n)      // prefer for local search
csp.atLeastNConstraintWithCounting(Set.of(b1, b2, b3), n)  // prefer for backtracking
csp.exactlyOneConstraint(Set.of(b1, b2, b3))
csp.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)      // constant or variable target
csp.maxConstraint(Set.of(v1, v2, v3), Operator.LEQ, 10)     // constant or variable target
csp.minConstraint(Set.of(v1, v2, v3), Operator.GEQ, 0)      // constant or variable target
csp.productConstraint(Set.of(v1, v2, v3), Operator.EQ, 24)
csp.divisionConstraint(dividend, divisor, Operator.EQ, 3)
csp.absoluteDifferenceConstraint(v1, v2, Operator.LEQ, 3)   // constant or variable target
csp.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, 10)  // constant or variable target
csp.linearBooleanConstraint(Map.of(b1, 2, b2, 3), Operator.LEQ, 10)  // over Variable<Boolean>; distinct name because of erasure
csp.countConstraint(Set.of(v1, v2, v3), value, Operator.EQ, 2)        // constant or variable target
csp.amongConstraint(Set.of(v1, v2, v3), Set.of(a, b), Operator.EQ, 2) // constant or variable target
csp.inverseConstraint(List.of(f1, f2, f3), List.of(g1, g2, g3))        // f[i]==j ↔ g[j-1]==i+1
csp.globalCardinalityConstraint(Set.of(v1, v2, v3), Map.of(a, 2, b, 1))   // globalCardinalityRangeConstraint for ranges
csp.nValueConstraint(Set.of(v1, v2, v3), count)     // count is a variable, so it can be minimized
csp.binPackingConstraint(bin, weights, capacities)
csp.cumulativeConstraint(starts, durations, resources, limit)
csp.disjunctiveConstraint(starts, durations)        // unary resource; edge-finding
csp.tuplesConstraint(Set.of(Assignment.of(...), ...))          // extensional (table)
csp.starredTuplesConstraint(Set.of(Map.of(v1, 1, v2, NaryStarredTuplesConstraint.STAR), ...))
csp.conflictTuplesConstraint(Set.of(Assignment.of(...), ...))  // forbidden combinations
csp.valueDisjunctionConstraint(Map.of(v1, 1, v2, 2))           // v1==1 OR v2==2
csp.valueConjunctionConstraint(Map.of(v1, 1, v2, 2), Operator.EQ)  // v1==1 AND v2==2 (also NEQ)
csp.increasingConstraint(List.of(v1, v2, v3))
csp.decreasingConstraint(List.of(v1, v2, v3))
csp.orderedConstraint(List.of(v1, v2, v3), Operator.LT)  // LT/LEQ/GEQ/GT
csp.lexConstraint(List.of(a1, a2), Operator.LEQ, List.of(b1, b2))
csp.predicateConstraint(Set.of(v1, v2, v3), predicate)
csp.circuitConstraint(List.of(s0, s1, s2))           // Hamiltonian circuit, 1-indexed successors
csp.subCircuitConstraint(List.of(s0, s1, s2))        // circuit through some nodes; self-loop = sits out
csp.diffnConstraint(xs, ys, widths, heights)          // diffnVariableConstraint for variable sizes
csp.regularConstraint(sequence, automaton)            // DFA-constrained sequence
```

**Reification**
```java
csp.reifyConstraint(b, constraint)    // b <-> constraint
csp.impliesConstraint(b, constraint)  // b -> constraint
```

`AndConstraint` wraps a set of constraints as a single one (chiefly as a reification body). A `Variable`-target sibling (`SumVariableConstraint`, `MaxVariableConstraint`, `CountVariableConstraint`, …) extends `NaryConstraint` directly because `UniformNaryConstraint#isSatisfiedBy` is `final`. Notable propagators with their own ADRs:
- GCC: [ADR-0016](docs/adr/0016-flow-based-gac-for-global-cardinality-constraint.md), [ADR-0017](docs/adr/0017-range-based-gac-for-global-cardinality-constraint.md)
- disjunctive: [ADR-0018](docs/adr/0018-disjunctive-edge-finding-propagator.md)
- tables: [ADR-0021](docs/adr/0021-bitset-indexed-gac-for-table-constraints.md)
- linear EQ: [ADR-0023](docs/adr/0023-subset-sum-gac-for-linear-equality-constraints.md)
- circuit vs sub-circuit: [ADR-0027](docs/adr/0027-xcsp3-circuit-is-a-sub-circuit.md)
- product: [ADR-0035](docs/adr/0035-signed-interval-and-coverage-product-propagation.md)
- cumulative: [ADR-0037](docs/adr/0037-energetic-reasoning-for-cumulative-constraints.md)

Everything else is in each class's own Javadoc.

### Key Conventions

- **Immutability**: `Assignment`, `Variable` and constraints use Lombok `@Value`; `CSP` uses `@Builder`/`@Singular`. Constraint subclasses use `@SuperBuilder` + `@EqualsAndHashCode(callSuper = true)`.
- **Lombok**: `@Value`, `@Builder`, `@SuperBuilder`, `@Singular`, `@Slf4j` are used extensively. Don't hand-write what Lombok provides.
- **Static factories**: use a constraint's `of()` rather than `.builder()...build()` in production code.
- **Null safety**: JSpecify `@NonNull`/`@Nullable` throughout; `Optional` for nullable returns.
- **Logging**: solvers and consistency algorithms use `@Slf4j`.
- **Assertions**: preconditions (e.g. equal list sizes) use Java `assert`.
- **Javadoc cross-references**: use `{@link}`/`{@linkplain}`, not `{@code}`, for any real program element — class, method or field (check the class's own fields/record components before assuming a name is unlinkable). `{@link}` breakage fails the build; `{@code}` silently rots. Only use `{@code}` for things that aren't linkable (local names, concepts, classes that can't be imported).
- **Erasure-colliding overloads get distinct names** (`linearBooleanConstraint`, `diffnVariableConstraint`, `globalCardinalityRangeConstraint`).
- **`Operator`** (EQ, NEQ, LT, GT, LEQ, GEQ) and **`LogicOperator`** (AND, OR, XOR, NAND, NOR, XNOR) live in the `constraints` package.
- **`ConstraintConsistency`** (`consistency`) — `apply(csp) → Optional<csp>`, plus `applyWithReason(csp, changedSinceLastRun)` for explanation. `FixpointConsistency.of(Type.class)` runs one constraint type to fixpoint with per-object dirty tracking ([ADR-0019](docs/adr/0019-fixpointconsistency-per-object-dirty-tracking.md)). `NogoodFixpointConsistency` is its nogood-specific sibling.
- **`Propagatable`** — `propagate(domains)`, `propagateWithReasons`, `explainInfeasible` and `isNecessarilySatisfied`. Implementations only ever `domains.get(v)` and never iterate the map, which is what lets `DomainOverlay` stand in for a full copy ([ADR-0031](docs/adr/0031-domain-overlays-instead-of-whole-map-copies.md)).
- **`BinaryDecomposable`** — n-ary constraints expressible as binary constraints; `isDecompositionComplete()` per [ADR-0008](docs/adr/0008-decomposition-completeness-flag.md).

### XCSP3 Instance Parsing

`parser.xcsp3` reads an XCSP3 instance via `org.xcsp:xcsp3-tools` ([ADR-0014](docs/adr/0014-xcsp3-parser-via-callback-library.md)).
- **Entry point**: `Xcsp3Parser.parse(Path) → Xcsp3Instance`, i.e. `csp`, an optional minimize-oriented `objective` and a `maximize` flag. A sum objective is always a real `LinearObjective`.
- **`Xcsp3CallbackHandler`** implements `XCallbacks2`, mapping each construct onto the builder API. Unsupported variants throw `UnsupportedXcsp3ConstraintException` rather than under-constraining the model. `<group>`/`<slide>` are expanded by the library.
- **Intension**: `<intension>` trees go through `recognizeConstraint`, which tries each registered `ConstraintRecognizer` in order and falls back to an unpropagated `PredicateConstraint` via `IntensionExpressionEvaluator`. The registration order is load-bearing — see `Xcsp3CallbackHandler#recognizers`. `TabulationRecognizer`'s position splits auxiliary-free recognizers from reifying ones ([ADR-0034](docs/adr/0034-tabulating-small-scope-intension-constraints.md)). `resolveVariable` is the value-producing half of the grammar, materializing memoized auxiliaries for `neg`/`add`/`sub`/`div`/`mod`.
- **Reification**: `loadCtr` stashes the current `XCtr#reification`, and `addOrReify` wraps constraints accordingly. `HALF_TO` is unsupported.
- **Deferred work**: 2D `noOverlap` (with redundant cumulative projections) and single-value counts sharing a list (consolidated into a GCC) are flushed in `toInstance()` ([ADR-0036](docs/adr/0036-joint-bounds-for-redundant-cumulative-capacity.md)).
- **`Xcsp3ProblemRunner`** (main sources) — a competition-style single-instance CLI (`s`/`o`/`v`/`c` lines). The `v` line is checkable by `xcsp3-tools`' `SolutionChecker`.

### Tests and Benchmarks

- **End-to-end examples** live in `io.github.rcrida.jcsp.solver.examples` (classic puzzles, scheduling, and CSPLib `ProbNNN*Test` instances), plus `Xcsp3InstanceTest` for parsed XCSP3 files.
- **Benchmark harnesses** live in `src/test/java`. None is run by surefire. All pin a fixed restart seed.
  - `NogoodPropagationBenchmark` — nogood-store overhead.
  - `CsplibBenchmarks` — JMH ([ADR-0013](docs/adr/0013-in-tree-jmh-benchmarks.md)).
  - `Xcsp3CompetitionRunner` — runs the bundled corpus at `src/test/resources/xcsp3/competition/`, one process per instance, cross-checking every solution with `SolutionChecker`. Use it for regression checks after parser or solver changes.

Run any of them with:

```bash
mvn test-compile
java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) <main-class> [args]
# <main-class>: io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark
#               org.openjdk.jmh.Main CsplibBenchmarks        (optional regex to filter)
#               io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CompetitionRunner [instanceDir] [timeLimitSeconds]   (default: bundled corpus, 60s)
```
