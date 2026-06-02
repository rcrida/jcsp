# CSP — Constraint Satisfaction Problem Solver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rcrida/jcsp)](https://central.sonatype.com/artifact/io.github.rcrida/jcsp)

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, and independent subproblem decomposition
- **Optimization**: branch-and-bound search via `getSolution(csp, objective)` and `getSolutions(csp, objective)` — returns the optimal assignment or an improving stream of assignments
- **Consistency preprocessing**: AC3 arc consistency and node consistency for domain pruning
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, comparator, logic, element, predicate, tuples), and n-ary (AllDiff, AtMostOne, AtLeastN, AtMostN, ExactlyOne, Sum, Linear, Count, GlobalCardinality, Tuples, Increasing, Decreasing, Lex, predicate)
- **Boolean domain**: `BooleanDomain` for modelling binary assignment problems (e.g. timetabling as a 0-1 matrix)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Heuristics**: MRV variable selection, LCV value ordering, and Minimum Degree variable elimination for tree decomposition
- **Local search**: `LocalSolver.Factory.INSTANCE` wires the full pipeline (NC + AC3 → independent subproblem decomposition → min-conflicts) and supports both satisfaction and optimization. Seeded by `RandomAssignmentFactory`, `GreedyAssignmentFactory`, or `FallbackAssignmentFactory` for hybrid restart strategies
- **Reification**: `ReifiedConstraint` (`b <-> body`) and `ImplicationConstraint` (`b -> body`) introduce boolean indicator variables that capture constraint satisfaction — enables soft constraints, counting satisfaction, and conditional constraints via `csp.reifyConstraint(b, constraint)` and `csp.impliesConstraint(b, constraint)`

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

Solver.Factory.INSTANCE.createSolver().getSolutions(csp).forEach(System.out::println);
```

### Optimization

Pass a `ToDoubleFunction<Assignment>` as the objective to minimise. The objective is called on partial assignments during branch-and-bound pruning, so unassigned variables must be handled gracefully (e.g. with `orElse`):

```java
Solver solver = Solver.Factory.INSTANCE.createSolver();

// Returns the assignment with the minimum cost
Optional<Assignment> best = solver.getSolution(csp, assignment ->
    assignment.getValue(x).orElse(0) + assignment.getValue(y).orElse(0));

// Returns a stream of improving assignments; the last element is the global optimum
solver.getSolutions(csp, objective).forEach(System.out::println);
```

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
builder.elementConstraint(index, result, array)             // result = array[index]  (1-based; MiniZinc table element)
builder.comparatorConstraint(v1, Operator.LEQ, v2)          // v1 <= v2  (any Comparable type; also EQ, NEQ, LT, GT, GEQ)
builder.logicConstraint(b1, LogicOperator.OR,  b2)          // b1 || b2  (Boolean vars; AND, OR, XOR, NAND, NOR, XNOR)
builder.biPredicateConstraint(v1, v2, biPredicate)          // biPredicate.test(v1, v2)
```

**N-ary**
```java
builder.sumConstraint(Set.of(v1, v2, v3), Operator.EQ, 10)          // v1 + v2 + v3 == 10  (also LEQ, GEQ, etc.)
builder.linearConstraint(Map.of(v1, 2, v2, 3), Operator.LEQ, 10)    // 2*v1 + 3*v2 <= 10  (weighted sum / linear)
builder.countConstraint(Set.of(v1, v2, v3), value, Operator.EQ, 2)           // number of variables equal to value == 2  (also LEQ, GEQ, etc.)
builder.globalCardinalityConstraint(Set.of(v1, v2, v3), Map.of(a, 2, b, 1))  // count(v, a)==2 AND count(v, b)==1  (open GCC)
builder.tuplesConstraint(Set.of(Assignment.of(...), ...))           // variable values must match one of the allowed assignments (order-independent)
builder.increasingConstraint(List.of(v1, v2, v3))                   // v1 <= v2 <= v3  (MiniZinc increasing)
builder.decreasingConstraint(List.of(v1, v2, v3))                   // v1 >= v2 >= v3  (MiniZinc decreasing)
builder.lexConstraint(List.of(a1,a2), Operator.LEQ, List.of(b1,b2)) // [a1,a2] lex<= [b1,b2]  (MiniZinc lex_lesseq)
builder.allDiffConstraint(Set.of(v1, v2, v3))                       // all different
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

The default solver (`Solver.Factory.INSTANCE.createSolver()`) applies strategies in order, each preprocessing the problem before delegating:

```
NodeConsistency → ArcConsistency (AC3) → IndependentSubproblems
    → TreeDecomposition → CutsetConditioning → TreeSolver / BranchAndBound(BacktrackingSearch)
```

Tree decomposition uses a domain-aware clique size limit (`d^targetTreewidth`, capped at 1,000,000) and only applies when the estimated tree complexity is less than the original search space. Cutset conditioning applies a practical three-tier complexity guard before conditioning.

## Local Search

`LocalSolver.Factory.INSTANCE` wires the local search pipeline:

```
NodeConsistency → ArcConsistency (AC3) → IndependentSubproblems → MinConflicts
```

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
    <version>2.13.0</version>
</dependency>
```

## Building

Requires JDK 21+ and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.
