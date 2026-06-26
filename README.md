# CSP — Constraint Satisfaction Problem Solver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rcrida/jcsp)](https://central.sonatype.com/artifact/io.github.rcrida/jcsp)

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, and independent subproblem decomposition
- **Optimization**: branch-and-bound search via `createSolver(csp, objective)` — returns a `BoundSolver` whose `getSolution()` finds the global optimum and `getSolutions()` streams improving assignments
- **Consistency preprocessing**: AC3 arc consistency, node consistency, AllDiff GAC (Régin 1994), SumConstraint and LinearConstraint bounds propagation, CountConstraint and AmongConstraint value-set propagation, InverseConstraint arc consistency, AtLeastN/AtMostN boolean forcing, CumulativeConstraint timetabling propagation, GlobalCardinalityConstraint value propagation, LexConstraint bounds propagation, and NaryTuplesConstraint table GAC — all run in a combined fixpoint loop so each propagator benefits from the others' reductions
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, comparator, logic, element, predicate, tuples), and n-ary (AllDiff, AtMostOne, AtLeastN, AtMostN, ExactlyOne, Sum, Linear, Count, Among, Inverse, GlobalCardinality, Cumulative, Tuples, Increasing, Decreasing, Lex, predicate)
- **Boolean domain**: `BooleanDomain` for modelling binary assignment problems (e.g. timetabling as a 0-1 matrix)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Heuristics**: MRV variable selection, LCV value ordering, and Minimum Degree variable elimination for tree decomposition
- **Local search**: `LocalSolver.Factory.INSTANCE` wires the full pipeline (NC + AC3 + bounds/value propagation → independent subproblem decomposition → terminal solver) and supports both satisfaction and optimization. Terminal solver is auto-selected: WalkSAT for all-boolean satisfaction CSPs without counting constraints, LargeNeighborhoodSearch for optimization with `ExactlyOneConstraint`s, MinConflicts otherwise. All `maxAttempts` restarts run in parallel; independent subproblems are also solved concurrently. Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory` for hybrid restart strategies
- **Reification**: `ReifiedConstraint` (`b <-> body`) and `ImplicationConstraint` (`b -> body`) introduce boolean indicator variables that capture constraint satisfaction — enables soft constraints, counting satisfaction, and conditional constraints via `csp.reifyConstraint(b, constraint)` and `csp.impliesConstraint(b, constraint)`
- **Real-valued variables**: `IntervalDomain` represents a continuous `[min, max]` range of `double`s. `SumConstraint`, `LinearConstraint`, `UnaryComparatorConstraint`, `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `LexConstraint`, and `CumulativeConstraint` all propagate over interval bounds, so many continuous problems are solved entirely by propagation
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

Mixed discrete/interval pairs are supported for `BinaryComparatorConstraint`, `BinaryOffsetConstraint`, and `AbsoluteDifferenceConstraint`: the discrete variable's numeric range is read to clip the interval variable's bounds, while the discrete side is left for AC3 to filter. Any other constraint type referencing an `IntervalDomain` variable is rejected with `IllegalArgumentException` at build time.

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
builder.elementConstraint(index, result, array)             // result = array[index]  (1-based; MiniZinc table element)
builder.comparatorConstraint(v1, Operator.LEQ, v2)          // v1 <= v2  (any Comparable type; also EQ, NEQ, LT, GT, GEQ)
builder.logicConstraint(b1, LogicOperator.OR,  b2)          // b1 || b2  (Boolean vars; AND, OR, XOR, NAND, NOR, XNOR)
builder.biPredicateConstraint(v1, v2, biPredicate)          // biPredicate.test(v1, v2)
```

**N-ary**
```java
builder.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)          // v1 + v2 + v3 == 10  (also LEQ, GEQ, etc.)
builder.maxConstraint(Set.of(v1, v2, v3), Operator.LEQ, 10)         // max(v1, v2, v3) <= 10  (also EQ, GEQ, LT, GT)
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
NodeConsistency → PropagationFixpoint(AC3 ↔ AllDiff GAC ↔ SumBounds ↔ LinearBounds ↔ CountValue ↔ InverseArc ↔ AmongValue ↔ AtLeastN/AtMostN ↔ CumulativeTimetable ↔ GlobalCardinalityValue ↔ LexBounds ↔ TuplesGAC)
    → IndependentSubproblems → TreeDecomposition → CutsetConditioning
    → TreeSolver / BacktrackingSearch(MAC + full propagator fixpoint)
```
For problems with `IntervalDomain` variables the fixpoint snaps non-singleton intervals to their midpoints, giving one concrete solution.

**Optimization** (`createSolver(csp, objective)`):
```
NodeConsistency → PropagationFixpoint → BisectionConditioning (continuous only)
    → BranchAndBound(BacktrackingSearch + MAC + full propagator fixpoint)
```
The fixpoint leaves intervals open for bisection. `BisectionConditioningSolver` bisects each non-singleton interval to within `DEFAULT_BISECTION_EPSILON`, repropagating bounds at each step; for purely discrete CSPs it is a passthrough. `BranchAndBound` then handles remaining discrete variables.

`PropagationFixpoint` runs all propagators in a combined fixpoint loop — each can expose new reductions the others exploit. Many highly-constrained problems (e.g. Zebra, Sudoku, MagicSquare) are solved entirely by propagation without any backtracking. During backtracking, `FULL_PROPAGATION_INFERENCE` fires all 17 propagators (including AllDiff GAC, GCC, cumulative timetabling, table GAC, and bounds propagators) to global fixpoint at every search node — not just during preprocessing.

Tree decomposition uses a domain-aware clique size limit (`d^targetTreewidth`, capped at 1,000,000) and is skipped when: the estimated tree complexity exceeds the search space, the constraint graph minimum degree ≥ targetTreewidth (guaranteeing the decomposer would fail), or when preprocessing fully determines the solution. When preprocessing produces all-singleton domains the solver short-circuits and returns the forced assignment directly without invoking any downstream stages. Cutset conditioning applies a practical three-tier complexity guard before conditioning.

## Local Search

`LocalSolver.Factory.INSTANCE` wires the local search pipeline:

```
NodeConsistency → AC3 → SumBounds → LinearBounds → CountValue → InverseArc → AmongValue → AtLeastN/AtMostN → CumulativeTimetable → GlobalCardinalityValue → LexBounds → TuplesGAC → IndependentSubproblems → WalkSAT / LNS / MinConflicts
```

The terminal solver is chosen automatically after preprocessing:
- **WalkSAT** — satisfaction only; used when all variable domains are boolean and the CSP has no `ExactlyOneConstraint` or `AtLeastNConstraint`
- **LargeNeighborhoodSearch** — optimization only; used when the CSP contains any `ExactlyOneConstraint`; enumerates destroy-repair moves over exactly-one slots
- **MinConflicts** — used otherwise for both satisfaction and optimization

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
    <version>2.24.0</version>
</dependency>
```

## Building

Requires JDK 21+ and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.
