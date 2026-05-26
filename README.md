# CSP — Constraint Satisfaction Problem Solver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rcrida/jcsp)](https://central.sonatype.com/artifact/io.github.rcrida/jcsp)

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, and independent subproblem decomposition
- **Optimization**: branch-and-bound search via `getSolution(csp, objective)` and `getSolutions(csp, objective)` — returns the optimal assignment or an improving stream of assignments
- **Consistency preprocessing**: AC3 arc consistency and node consistency for domain pruning
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, predicate, tuples), and n-ary (AllDiff, AtMostOne, AtLeastN, AtMostN, ExactlyOne, predicate)
- **Boolean domain**: `BooleanDomain` for modelling binary assignment problems (e.g. timetabling as a 0-1 matrix)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Heuristics**: MRV variable selection, LCV value ordering, and Minimum Degree variable elimination for tree decomposition
- **Local search**: `MinConflictsSolver` supports both satisfaction and optimization using weighted min-conflicts with lexicographic value selection and iterated restarts; constructed via `MinConflictsSolver.of(maxAttempts, maxSteps, factory)` and seeded by `RandomAssignmentFactory` or `GreedyAssignmentFactory`. `LocalSolver.Factory.INSTANCE` wires the full pipeline: NC + AC3 preprocessing → independent subproblem decomposition → min-conflicts search
- **Independent subproblem local solver**: `IndependentSubproblemLocalSolver` decomposes a CSP into independent subproblems and solves each with a delegate `LocalSolver`, mirroring `IndependentSubproblemSolver` for the local search path

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

```java
builder.equalsConstraint(v1, v2)                       // v1 == v2
builder.notEqualsConstraint(v1, v2)                    // v1 != v2
builder.notEqualsChainConstraint(v1, v2, v3)           // AllDiff over a chain
builder.allDiffConstraint(v1, v2, v3)                  // all different
builder.atMostOneConstraint(Set.of(v1, v2, v3))        // at most one boolean variable is true (AC3 decomposition)
builder.atLeastNConstraint(Set.of(v1, v2, v3), n)      // at least n boolean variables are true
builder.atMostNConstraint(Set.of(v1, v2, v3), n)       // at most n boolean variables are true
builder.exactlyOneConstraint(Set.of(v1, v2, v3))       // exactly one boolean variable is true
builder.offsetConstraint(v1, v2, offset)               // v1 == v2 + offset
builder.biPredicateConstraint(v1, v2, predicate)       // custom binary predicate
builder.predicateConstraint(predicate, v1, v2, v3)     // custom n-ary predicate
```

## Solver Chain

The default solver applies strategies in order, each preprocessing the problem before delegating:

```
NodeConsistency → ArcConsistency (AC3) → IndependentSubproblems
    → TreeDecomposition → CutsetConditioning → TreeSolver / BranchAndBound(BacktrackingSearch)
```

Tree decomposition uses a domain-aware clique size limit (`d^targetTreewidth`, capped at 1,000,000) and only applies when the estimated tree complexity is less than the original search space. Cutset conditioning applies a practical three-tier complexity guard before conditioning.

## Installation

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.8.0</version>
</dependency>
```

## Building

Requires JDK 21+ and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.
