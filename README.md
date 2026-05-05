# CSP — Constraint Satisfaction Problem Solver

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, independent subproblem decomposition, and min-conflicts local search
- **Consistency preprocessing**: AC3 arc consistency and node consistency for domain pruning
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, predicate, tuples), and n-ary (AllDiff, predicate)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Heuristics**: Minimum Remaining Values (MRV) variable selection and Least Constraining Value (LCV) ordering

## Usage

```java
Variable v1 = Variable.Factory.INSTANCE.create("V1");
Variable v2 = Variable.Factory.INSTANCE.create("V2");
Variable v3 = Variable.Factory.INSTANCE.create("V3");

ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
    .variable(v1).domain(v1, IntRangeDomain.of(1, 3))
    .variable(v2).domain(v2, IntRangeDomain.of(1, 3))
    .variable(v3).domain(v3, IntRangeDomain.of(1, 3))
    .allDiffConstraint(v1, v2, v3)
    .build();

Solver.Factory.INSTANCE.createSolver().getSolutions(csp).forEach(System.out::println);
```

## Solver Chain

The default solver applies strategies in order, each preprocessing the problem before delegating:

```
NodeConsistency → ArcConsistency (AC3) → IndependentSubproblems
    → TreeDecomposition → CutsetConditioning → TreeSolver / BacktrackingSearch
```

## Building

Requires JDK 25 and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.
