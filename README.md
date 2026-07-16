# CSP — Constraint Satisfaction Problem Solver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rcrida/jcsp)](https://central.sonatype.com/artifact/io.github.rcrida/jcsp)

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## 🧠 Why `jcsp`?

Traditional Java constraint satisfaction problem (CSP) solvers were designed over two decades ago. While powerful for single-threaded tasks, they rely heavily on **global mutable state**, **in-place arrays**, and **imperative trailing stacks**. This legacy architecture makes multi-threading dangerous, cloud integration clunky, and debugging a nightmare.

`jcsp` is built from scratch for **Modern Java (21+)**, reimagining constraint solving through a pure functional and immutable lens.

### 🚀 Key Architectural Advantages

* **Immutable Core Objects:** Variables, domains, solver assignments, and constraint objects are deeply immutable — safe to share across threads. Each `getSolutions()` stream must be consumed sequentially; for concurrent search, create independent solver instances per thread or use the built-in parallel local search restarts.
* **Parallel Local Search:** All local search restart attempts run concurrently via parallel streams, so increasing `maxAttempts` automatically exploits multiple CPU cores. Independent subproblems are also solved concurrently during both systematic and local search.
* **Lazy Streaming API:** `jcsp` exposes solutions as a native, lazy `Stream<Assignment>` rather than callbacks or blocking calls. Map, filter, limit, and collect solutions incrementally using standard Java Stream pipelines.
* **Isolated State Architecture:** Traditional solvers anchor their search to a single, mutable global state. In `jcsp`, the search engine passes lightweight, isolated state objects down the execution path, making search logic predictable and easy to test.
* **100% Instruction and Branch Coverage:** Every production code path is executed and every branch is covered by the test suite — enforced as a build gate by JaCoCo.

### 🛠️ The Architecture at a Glance

| Feature | Traditional Java Solvers | `jcsp` Solver |
| :--- | :--- | :--- |
| **State Model** | Stateful & Mutable (Trailing) | **Deeply Immutable Value Objects** |
| **Concurrency** | Dangerous / Requires Wrappers | **Immutable Core; Parallel Local Search Restarts** |
| **API Paradigm** | Imperative / Event Observers | **Functional / Lazy Streams** |
| **Java Baseline** | Legacy Compatibility (JDK 8/11) | **Modern Java Baseline (JDK 21+)** |
| **Best For** | Heavy, Monolithic CPU Grinds | **Cloud Microservices, Streaming APIs, Parallel Search** |

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, and independent subproblem decomposition
- **Optimization**: branch-and-bound search via `createSolver(csp, objective)` — returns a `BoundSolver` whose `getSolution()` finds the global optimum and `getSolutions()` streams improving assignments
- **Consistency preprocessing**: AC3 arc consistency, node consistency, AllDiff GAC (Régin 1994), SumConstraint and LinearConstraint bounds propagation, CountConstraint and AmongConstraint value-set propagation, InverseConstraint arc consistency, AtLeastN/AtMostN boolean forcing, CumulativeConstraint timetabling propagation, GlobalCardinalityConstraint value propagation, LexConstraint bounds propagation, MaxConstraint and MinConstraint bounds propagation, ProductConstraint bounds propagation, DivisionConstraint bounds propagation, NaryElementConstraint domain filtering, NaryTuplesConstraint table GAC, CircuitConstraint Hamiltonian propagation, DiffnConstraint compulsory-part propagation, and RegularConstraint forward-backward DP propagation — all run in a combined fixpoint loop so each propagator benefits from the others' reductions
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, comparator, logic, element over fixed array, absolute-difference, division, predicate, tuples), and n-ary (AllDiff, AtMostOne, AtLeastN, AtMostN, ExactlyOne, Sum, Product, Linear, Count, Among, Inverse, GlobalCardinality, Cumulative, Max, Min, Element over variables, Tuples, Increasing, Decreasing, Lex, predicate, Circuit, Diffn, Regular)
- **Boolean domain**: `BooleanDomain` for modelling binary assignment problems (e.g. timetabling as a 0-1 matrix)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Solver configuration**: `createSolver(csp, config)` takes an optional `SolverConfig` builder bundling search limits and nogood-learning behavior. `SolverLimits` caps work by node count and/or wall-clock time; `getSolution()` throws `LimitExceededException` (carrying `Statistics`) when a limit is hit — distinguishable from a genuine UNSAT result (`Optional.empty()`). `getSolutions()` truncates the stream silently instead. `conflictExplainer` can be set to `NullConflictExplainer.INSTANCE` to disable nogood learning (CDCL) entirely, for problem shapes where learned nogoods rarely get reused
- **Heuristics**: dom/wdeg variable ordering with Luby restarts (Boussemart et al. 2004) for the satisfaction terminal solver; MRV variable selection for the optimization chain; LCV value ordering; and Minimum Degree variable elimination for tree decomposition
- **Nogood learning**: the satisfaction terminal solver records a learned nogood (a partial assignment guaranteed to fail) on every domain wipeout, explained by re-running MAC and the propagation fixpoint with reason tracking; future search nodes whose assignment subsumes a learned nogood are pruned immediately, and nogoods persist across Luby restarts. Nogood bookkeeping is cached across search nodes, so a growing nogood set pays for itself in pruning power without a matching rise in per-node overhead
- **Local search**: `LocalSolver.Factory.INSTANCE` wires the full pipeline (NC + AC3 + bounds/value propagation → independent subproblem decomposition → terminal solver) and supports both satisfaction and optimization. Terminal solver is auto-selected: WalkSAT for all-boolean satisfaction CSPs without counting constraints, LargeNeighborhoodSearch for optimization with `ExactlyOneConstraint`s, and otherwise `RaceLocalSolver` runs MinConflicts and TabuSearch concurrently and returns whichever finds a solution first. All `maxAttempts` restarts run in parallel; independent subproblems are also solved concurrently. Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory` for hybrid restart strategies
- **Tabu search**: `TabuSearchSolver` extends min-conflicts move selection with a short-term memory that forbids reverting a variable to the value it just held for `tabuTenure` steps, unless doing so strictly improves on the best total conflict weight seen so far this attempt (the aspiration criterion) — breaks the two-step cycles plain min-conflicts can get stuck repeating
- **Reification**: `ReifiedConstraint` (`b <-> body`) and `ImplicationConstraint` (`b -> body`) introduce boolean indicator variables that capture constraint satisfaction — enables soft constraints, counting satisfaction, and conditional constraints via `csp.reifyConstraint(b, constraint)` and `csp.impliesConstraint(b, constraint)`
- **Real-valued variables**: `IntervalDomain` represents a continuous `[min, max]` range of `double`s. `SumConstraint`, `LinearConstraint`, `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `DivisionConstraint`, `ProductConstraint`, `LexConstraint`, `MaxConstraint`, `MinConstraint`, `CumulativeConstraint`, and `DiffnConstraint` (origin variables) all propagate over interval bounds, so many continuous problems are solved entirely by propagation. `IncreasingConstraint`, `DecreasingConstraint`, `UnaryPredicateConstraint`, `BinaryPredicateConstraint`, `PredicateConstraint`, `ReifiedConstraint`, `ImplicationConstraint`, and `NaryElementConstraint` also accept `IntervalDomain` variables (no dedicated interval propagation, but resolved correctly via search plus the final satisfaction check)
- **Continuous optimization**: `createSolver(csp, objective)` auto-detects `IntervalDomain` variables and explores their feasible region via `BisectionConditioningSolver` — recursively bisecting intervals to within `DEFAULT_BISECTION_EPSILON (1e-3)`, repropagating bounds at each step, then filtering the resulting feasible points by the objective; `getSolution()` returns the global optimum and `getSolutions()` streams improving assignments

## Usage

```java
Variable.Factory F = Variable.Factory.INSTANCE;
Variable<Integer> v1 = F.create("v1");
Variable<Integer> v2 = F.create("v2");
Variable<Integer> v3 = F.create("v3");

ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
    .variableDomain(v1, IntRangeDomain.of(1, 3))
    .variableDomain(v2, IntRangeDomain.of(1, 3))
    .variableDomain(v3, IntRangeDomain.of(1, 3))
    .allDiffConstraint(Set.of(v1, v2, v3))
    .build();

Solver.Factory.INSTANCE.createSolver(csp).getSolutions().forEach(System.out::println);
```

### Optimization

Pass a `ToDoubleFunction<Assignment>` as the objective to minimise. `createSolver(csp, objective)` returns a `BoundSolver`: call `getSolution()` for the global optimum or `getSolutions()` for the full improving stream. The objective is called on partial assignments during branch-and-bound pruning, so unassigned variables must be handled gracefully (e.g. with `orElse`):

```java
// Returns the assignment with the minimum cost
Optional<Assignment> best = Solver.Factory.INSTANCE.createSolver(csp, assignment ->
    assignment.getValue(x).orElse(0) + assignment.getValue(y).orElse(0)).getSolution();

// Returns a stream of improving assignments; the last element is the global optimum
Solver.Factory.INSTANCE.createSolver(csp, objective).getSolutions().forEach(System.out::println);
```

### Real-valued variables

`IntervalDomain` models a continuous `[min, max]` range of `double`s. The following constraint types support `IntervalDomain` variables and propagate over interval bounds using interval arithmetic:

- `SumConstraint` / `LinearConstraint` (with a `Double` bound) — linear arithmetic propagation
- `UnaryComparatorConstraint` — clips a single interval variable's bounds (e.g. `x >= 3.0`)
- `BinaryComparatorConstraint` — clips both variables' bounds relative to each other (e.g. `x <= y`)
- `BinaryOffsetConstraint` — clips bounds accounting for the offset (e.g. `x + 3.0 == y`)
- `AbsoluteDifferenceConstraint` — clips bounds symmetrically for `LEQ`/`LT` (`x ∈ [y.min−d, y.max+d]`); detects infeasibility for `EQ` and `GEQ`/`GT` (e.g. `|x - y| <= 2.0`)
- `LexConstraint` — clips the first non-forced-equal position's lesser upper bound and greater lower bound
- `CumulativeConstraint` — event-based timetabling propagator; start variables may be `Variable<Double>` with `IntervalDomain` (continuous scheduling) or `Variable<Integer>` with `IntRangeDomain` (integer scheduling); durations, resources, and limit are `double` in both cases

`BinaryComparatorConstraint`, `BinaryOffsetConstraint`, and `AbsoluteDifferenceConstraint` narrow via a shared bounds helper that works across any combination of discrete and interval domains, so a plain discrete/discrete pair gets real value-deletion narrowing too — not just mixed discrete/interval pairs, and not left entirely to AC3. (`BinaryComparatorConstraint` also backs non-numeric orderings like `IncreasingConstraint`/`DecreasingConstraint`, so it only applies this narrowing when both sides are actually numeric.)

A second group of constraint types also accepts `IntervalDomain` variables, but without dedicated interval propagation — correctness instead rests on the same final satisfaction check every solver path already runs before returning a solution, so they're resolved by search rather than bounds narrowing:

- `IncreasingConstraint` / `DecreasingConstraint` — order constraints over a sequence (e.g. `x <= y <= z`)
- `UnaryPredicateConstraint` / `BinaryPredicateConstraint` / `PredicateConstraint` — arbitrary user predicates over one, two, or a set of variables
- `ReifiedConstraint` / `ImplicationConstraint` — reification and half-reification of another constraint via a boolean indicator
- `NaryElementConstraint` — `result = vars[index]` with continuous `result`/`vars` and a discrete `index`

Any other constraint type referencing an `IntervalDomain` variable is rejected with `IllegalArgumentException` at build time.

```java
Variable<Double> rent = F.create("rent");
Variable<Double> food = F.create("food");
Variable<Double> transport = F.create("transport");

ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
    .variableDomain(rent, IntervalDomain.of(60.0, 60.0))
    .variableDomain(food, IntervalDomain.of(0.0, 100.0))
    .variableDomain(transport, IntervalDomain.of(0.0, 100.0))
    .sumConstraint(Set.of(rent, food), Operator.EQ, 100.0)            // rent + food == 100
    .linearConstraint(Map.of(rent, 1.0, transport, 5.0), Operator.EQ, 120.0)  // rent + 5*transport == 120
    .build();

// Resolved to a single solution by bounds propagation alone: food=40.0, transport=12.0
Solver.Factory.INSTANCE.createSolver(csp).getSolutions().forEach(System.out::println);
```

### Continuous optimization

`createSolver(csp, objective)` auto-detects `IntervalDomain` variables. `BisectionConditioningSolver` recursively bisects each non-singleton interval to within `DEFAULT_BISECTION_EPSILON` (`1e-3`), repropagating bounds at each step to prune infeasible halves, and returns the feasible point with the lowest objective value. Unlike branch-and-bound, the objective is always called on **complete** assignments, so `orElseThrow()` is safe:

```java
Variable<Double> x = F.create("x");
Variable<Double> y = F.create("y");

ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
    .variableDomain(x, IntervalDomain.of(0.0, 10.0))
    .variableDomain(y, IntervalDomain.of(0.0, 10.0))
    .sumConstraint(Set.of(x, y), Operator.EQ, 7.0)   // x + y = 7
    .build();

// Minimise (x−2)² subject to x+y=7, x,y∈[0,10]  →  x≈2, y≈5
Optional<Assignment> best = Solver.Factory.INSTANCE
    .createSolver(csp, a -> Math.pow((Double) a.getValue(x).orElseThrow() - 2.0, 2))
    .getSolution();
```

`getSolutions()` on the returned `BoundSolver` gives a lazy stream of improving assignments (each strictly better than the previous); the last element is the global optimum found within the bisection resolution.

### Solver configuration

`createSolver` takes an optional `SolverConfig` — a builder with defaults, so new knobs don't mean new overloads. `SolverConfig.builder().build()` reproduces the unconfigured defaults exactly (unlimited search, standard nogood learning):

```java
BoundSolver solver = Solver.Factory.INSTANCE.createSolver(csp,
    SolverConfig.builder()
        .limits(SolverLimits.ofNodes(10_000))
        .build());
```

**Search limits** — `SolverLimits` caps work by node count and/or wall-clock time:

```java
SolverLimits.ofNodes(10_000)                          // stop after at most 10,000 node assignments
SolverLimits.ofTime(Duration.ofSeconds(5))             // stop after at most 5 seconds of wall-clock time
SolverLimits.of(10_000, Duration.ofSeconds(5))         // both together
```

When a limit is exceeded, `getSolution()` throws `LimitExceededException` containing `Statistics` (nodes explored, backtracks, etc.) so you can distinguish a limit-hit from a genuine UNSAT:

```java
try {
    Optional<Assignment> solution = solver.getSolution();
    // Optional.empty() means genuinely UNSAT
} catch (LimitExceededException e) {
    // limit was hit before search completed
    System.out.println("Explored " + e.getStatistics().getNodesExplored() + " nodes");
}
```

`getSolutions()` truncates the stream silently when a limit is hit — useful for anytime search where partial results are acceptable.

**Disabling nogood learning (CDCL)** — the satisfaction chain's terminal solver learns a nogood on every domain wipeout by default, which pays off when learned nogoods get reused later in the search. For problem shapes where they rarely do (e.g. searches that backtrack very little, or where the same conflict rarely recurs), that explanation cost is pure overhead. Pass `NullConflictExplainer.INSTANCE` to skip it entirely — dom/wdeg variable-ordering weight updates are unaffected, since they're a separate heuristic:

```java
BoundSolver solver = Solver.Factory.INSTANCE.createSolver(csp,
    SolverConfig.builder()
        .conflictExplainer(NullConflictExplainer.INSTANCE)
        .build());
```

(`conflictExplainer` only affects the satisfaction chain — the optimization chain's branch-and-bound search doesn't do nogood learning at all.)

### Constraint builder methods

**Unary**
```java
builder.equalsConstraint(v, value)                          // v == value
builder.notEqualsConstraint(v, value)                       // v != value
builder.predicateConstraint(v, predicate)                   // predicate.test(v)
builder.comparatorConstraint(v, Operator.GEQ, value)        // v >= value  (Number types; also LT, GT, LEQ, EQ, NEQ)
```

**Binary**
```java
builder.equalsConstraint(v1, v2)                            // v1 == v2
builder.notEqualsConstraint(v1, v2)                         // v1 != v2
builder.notEqualsChainConstraint(List.of(v1, v2, v3))       // v1 != v2, v2 != v3 (consecutive pairs)
builder.offsetConstraint(v1, offset, Operator.EQ, v2)       // v1 + offset == v2  (also LT, GT, LEQ, GEQ, NEQ)
builder.absoluteDifferenceConstraint(v1, v2, Operator.LEQ, bound)  // |v1 - v2| <= bound  (also EQ, GEQ, GT, LT, NEQ)
builder.elementConstraint(index, result, array)             // result = array[index]  (1-based; array is a fixed List<T>)
builder.elementVariableConstraint(index, result, vars)      // result = vars[index]   (1-based; vars is a List<Variable<T>>; use for permutation composition, channeling, etc.)
builder.comparatorConstraint(v1, Operator.LEQ, v2)          // v1 <= v2  (any Comparable type; also EQ, NEQ, LT, GT, GEQ)
builder.logicConstraint(b1, LogicOperator.OR,  b2)          // b1 || b2  (Boolean vars; AND, OR, XOR, NAND, NOR, XNOR)
builder.biPredicateConstraint(v1, v2, biPredicate)          // biPredicate.test(v1, v2)
```

**N-ary**
```java
builder.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)          // v1 + v2 + v3 == 10  (also LEQ, GEQ, etc.)
builder.maxConstraint(Set.of(v1, v2, v3), Operator.LEQ, 10)         // max(v1, v2, v3) <= 10  (also EQ, GEQ, LT, GT)
builder.productConstraint(Set.of(v1, v2, v3), Operator.EQ, 24)      // v1*v2*v3 == 24  (also LEQ, GEQ; requires strictly positive domain mins)
builder.divisionConstraint(dividend, divisor, Operator.EQ, 3)        // dividend/divisor == 3  (also LEQ, GEQ; requires strictly positive domain mins for both)
builder.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, 10)    // 2*v1 + 3*v2 <= 10  (weighted sum / linear)
builder.countConstraint(Set.of(v1, v2, v3), value, Operator.EQ, 2)                    // number of variables equal to value == 2  (also LEQ, GEQ, etc.)
builder.amongConstraint(Set.of(v1, v2, v3), Set.of(a, b), Operator.EQ, 2)             // number of variables with value in {a,b} == 2  (MiniZinc among)
builder.inverseConstraint(List.of(f1, f2, f3), List.of(g1, g2, g3))                   // f[i]==j ↔ g[j-1]==i+1  (MiniZinc inverse; 1-based values)
builder.globalCardinalityConstraint(Set.of(v1, v2, v3), Map.of(a, 2, b, 1))           // count(v, a)==2 AND count(v, b)==1  (open GCC)
builder.tuplesConstraint(Set.of(Assignment.of(...), ...))           // variable values must match one of the allowed assignments (order-independent)
builder.increasingConstraint(List.of(v1, v2, v3))                   // v1 <= v2 <= v3  (MiniZinc increasing)
builder.decreasingConstraint(List.of(v1, v2, v3))                   // v1 >= v2 >= v3  (MiniZinc decreasing)
builder.lexConstraint(List.of(a1,a2), Operator.LEQ, List.of(b1,b2)) // [a1,a2] lex<= [b1,b2]  (MiniZinc lex_lesseq)
builder.allDiffConstraint(Set.of(v1, v2, v3))                       // all different
builder.cumulativeConstraint(starts, durations, resources, limit)    // resource-bounded scheduling (MiniZinc cumulative)
builder.atMostOneConstraint(Set.of(b1, b2, b3))                     // at most one boolean is true  (AC3 decomposition)
builder.atMostNConstraint(Set.of(b1, b2, b3), n)                    // at most n booleans are true
builder.atLeastNConstraint(Set.of(b1, b2, b3), n)                   // at least n booleans are true  (prefer for local search)
builder.atLeastNConstraintWithCounting(Set.of(b1, b2, b3), n)       // at least n booleans are true via carry-chain  (prefer for backtracking)
builder.exactlyOneConstraint(Set.of(b1, b2, b3))                    // exactly one boolean is true
builder.predicateConstraint(Set.of(v1, v2, v3), predicate)          // predicate.test(assignment) over a set of variables
builder.circuitConstraint(List.of(s0, s1, s2))                      // Hamiltonian circuit: successors[i] is the 1-indexed next node after node i+1 (MiniZinc circuit)
builder.diffnConstraint(xs, ys, widths, heights)                     // pairwise non-overlapping 2D rectangles; origin variables accept IntRangeDomain or IntervalDomain (MiniZinc diffn)
builder.regularConstraint(sequence, automaton)                       // sequence values must be accepted by the given DFA (MiniZinc regular); build the automaton with Automaton.of(numStates, initialState, acceptingStates, transitions)
```

**Reification**
```java
builder.reifyConstraint(b, constraint)                      // b <-> constraint  (b is true iff constraint is satisfied)
builder.impliesConstraint(b, constraint)                    // b -> constraint   (when b is true, constraint must hold)
```

## Solver Chain

`Solver.Factory.INSTANCE` builds two distinct chains, each returning a `BoundSolver`:

**Satisfaction** (`createSolver(csp)`):
```
NodeConsistency → PropagationFixpoint(AC3 ↔ AllDiff GAC ↔ SumBounds ↔ LinearBounds ↔ CountValue ↔ InverseArc ↔ AmongValue ↔ AtLeastN/AtMostN ↔ CumulativeTimetable ↔ GlobalCardinalityValue ↔ LexBounds ↔ MaxBounds ↔ MinBounds ↔ ElementDomains ↔ TuplesGAC ↔ ProductBounds ↔ DivisionBounds ↔ CircuitPropagation ↔ DiffnCompulsoryParts ↔ RegularDP)
    → IndependentSubproblems → TreeDecomposition → CutsetConditioning
    → TreeSolver / DomWdegLubySearch(dom/wdeg + Luby restarts + MAC + nogood learning)
```
For problems with `IntervalDomain` variables the fixpoint snaps non-singleton intervals to their midpoints, giving one concrete solution.

**Optimization** (`createSolver(csp, objective)`):
```
NodeConsistency → PropagationFixpoint → BisectionConditioning (continuous only)
    → BranchAndBound(BacktrackingSearch + MAC + full propagator fixpoint)
```
The fixpoint leaves intervals open for bisection. `BisectionConditioningSolver` bisects each non-singleton interval to within `DEFAULT_BISECTION_EPSILON`, repropagating bounds at each step; for purely discrete CSPs it is a passthrough. `BranchAndBound` then handles remaining discrete variables.

`PropagationFixpoint` runs all propagators in a combined fixpoint loop — each can expose new reductions the others exploit. Many highly-constrained problems (e.g. Zebra, Sudoku, MagicSquare) are solved entirely by propagation without any backtracking. During search, `FULL_PROPAGATION_INFERENCE` fires all propagators (including AllDiff GAC, GCC, cumulative timetabling, table GAC, element domain filtering, and bounds propagators) to global fixpoint at every search node — not just during preprocessing.

`DomWdegLubySearch` — the terminal solver for general CSPs — combines **dom/wdeg variable ordering** (Boussemart et al. 2004) with **Luby restarts**. Each constraint starts with weight 1; domain wipeouts during MAC inference increment the weights of active constraints on the failing variable. The selector then picks `argmin(domainSize / weightedDegree)`, steering search away from costly regions. On every domain wipeout the failing assignment is also explained — via `MacAndFixpointConflictExplainer`, which re-runs MAC then the propagation fixpoint with reason tracking — and the resulting nogood is recorded in a shared `NogoodStore`; later candidate assignments that subsume a learned nogood are pruned before consistency checking. `getSolutions()` returns a lazy stream of all solutions with accumulated weight learning; `getSolution()` applies Luby restarts — the failure budget follows 1, 1, 2, 1, 1, 2, 4, … (×`DEFAULT_LUBY_UNIT = 100`) and both weights and learned nogoods are preserved across restarts.

Tree decomposition uses a domain-aware clique size limit (`d^targetTreewidth`, capped at 1,000,000) and is skipped when: the estimated tree complexity exceeds the search space, the constraint graph minimum degree ≥ targetTreewidth (guaranteeing the decomposer would fail), or when preprocessing fully determines the solution. When preprocessing produces all-singleton domains the solver short-circuits and returns the forced assignment directly without invoking any downstream stages. Cutset conditioning applies a practical three-tier complexity guard before conditioning; `getSolution()` fans each cutset assignment's tree solve out to a virtual thread and returns the first successful result, exploiting the independence of conditioned subproblems.

## Local Search

`LocalSolver.Factory.INSTANCE` wires the local search pipeline:

```
NodeConsistency → AC3 → SumBounds → LinearBounds → CountValue → InverseArc → AmongValue → AtLeastN/AtMostN → CumulativeTimetable → GlobalCardinalityValue → LexBounds → MaxBounds → MinBounds → ElementDomains → TuplesGAC → ProductBounds → DivisionBounds → CircuitPropagation → DiffnCompulsoryParts → RegularDP → IndependentSubproblems → WalkSAT / LNS / (MinConflicts race TabuSearch)
```

The terminal solver is chosen automatically after preprocessing:
- **WalkSAT** — satisfaction only; used when all variable domains are boolean and the CSP has no `ExactlyOneConstraint` or `AtLeastNConstraint`
- **LargeNeighborhoodSearch** — optimization only; used when the CSP contains any `ExactlyOneConstraint`; enumerates destroy-repair moves over exactly-one slots
- **MinConflicts vs TabuSearch** — used otherwise for both satisfaction and optimization; `RaceLocalSolver` runs both concurrently and returns whichever finds a result first (a shared cancellation token stops the loser at its next search step) rather than picking one via a heuristic — a routing heuristic for this exact pair was tried and falsified before for a different pair of solvers (`BacktrackingSearch` vs `DomWdegLubySearch`)

Create a local solver and call `getLocalSolution` for satisfaction, or pass an objective for optimization:

```java
LocalSolver solver = LocalSolver.Factory.INSTANCE.createLocalSolver(
    10,                                  // maxAttempts: restarts from a fresh initial assignment
    1000,                                // maxSteps: min-conflicts steps per attempt
    RandomAssignmentFactory.INSTANCE     // or GreedyAssignmentFactory.INSTANCE
);

// Satisfaction
Optional<Assignment> solution = solver.getLocalSolution(csp);

// Optimization — returns the best assignment found across all attempts
Optional<Assignment> best = solver.getLocalSolution(csp, assignment ->
    assignment.getValue(cost).orElse(0.0));
```

**Initial assignment factories:**
- `RandomAssignmentFactory.INSTANCE` — assigns random values; good for diverse restarts
- `GreedyAssignmentFactory.INSTANCE` — minimises constraint violations greedily; good for a warm first attempt
- `FallbackAssignmentFactory` — uses a primary factory for the first N calls, then falls back to a secondary (e.g. greedy first attempt, random restarts thereafter)

```java
InitialAssignmentFactory factory = FallbackAssignmentFactory.builder()
    .primary(GreedyAssignmentFactory.INSTANCE)
    .primaryCount(1)
    .fallback(RandomAssignmentFactory.INSTANCE)
    .build();
```

## Installation

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.33.0</version>
</dependency>
```

## Building

Requires JDK 21+ and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.

End-to-end integration tests (classic CSP problems and real-world scheduling examples) live in `io.github.rcrida.jcsp.solver.examples`.
