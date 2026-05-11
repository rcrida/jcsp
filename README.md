# CSP — Constraint Satisfaction Problem Solver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rcrida/jcsp)](https://central.sonatype.com/artifact/io.github.rcrida/jcsp)

A Java library implementing classic AI algorithms for solving Constraint Satisfaction Problems (CSPs).

## Features

- **Multiple solving strategies**: backtracking search, tree solver, cutset conditioning, tree decomposition, and independent subproblem decomposition
- **Consistency preprocessing**: AC3 arc consistency and node consistency for domain pruning
- **Flexible constraint types**: unary, binary (equals, not-equals, offset, predicate, tuples), and n-ary (AllDiff, predicate)
- **Functional style**: immutable value objects, composable solver decorators, and a lazy `Stream<Assignment>` API throughout
- **Heuristics**: MRV variable selection, LCV value ordering, and Minimum Degree variable elimination for tree decomposition

## Usage

```java
Variable.Factory F = Variable.Factory.INSTANCE;
Variable v1 = F.create("v1");
Variable v2 = F.create("v2");
Variable v3 = F.create("v3");

ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
    .variableDomain(v1, IntRangeDomain.of(1, 3))
    .variableDomain(v2, IntRangeDomain.of(1, 3))
    .variableDomain(v3, IntRangeDomain.of(1, 3))
    .allDiffConstraint(v1, v2, v3)
    .build();

Solver.Factory.INSTANCE.createSolver().getSolutions(csp).forEach(System.out::println);
```

### Constraint builder methods

```java
builder.equalsConstraint(v1, v2)                       // v1 == v2
builder.notEqualsConstraint(v1, v2)                    // v1 != v2
builder.notEqualsChainConstraint(v1, v2, v3)           // AllDiff over a chain
builder.allDiffConstraint(v1, v2, v3)                  // all different
builder.offsetConstraint(v1, v2, offset)               // v1 == v2 + offset
builder.biPredicateConstraint(v1, v2, predicate)       // custom binary predicate
builder.predicateConstraint(predicate, v1, v2, v3)     // custom n-ary predicate
```

## Solver Chain

The default solver applies strategies in order, each preprocessing the problem before delegating:

```
NodeConsistency → ArcConsistency (AC3) → IndependentSubproblems
    → TreeDecomposition → CutsetConditioning → TreeSolver / BacktrackingSearch
```

Tree decomposition uses a domain-aware clique size limit (`d^targetTreewidth`, capped at 1,000,000) and only applies when the estimated tree complexity is less than the original search space. Cutset conditioning applies a practical three-tier complexity guard before conditioning.

## Installation

```xml
<dependency>
    <groupId>io.github.rcrida</groupId>
    <artifactId>jcsp</artifactId>
    <version>2.1.0</version>
</dependency>
```

## Building

Requires JDK 25 and Maven 3.x.

```bash
mvn test          # run all tests
mvn verify        # run tests + generate coverage report (target/site/jacoco/index.html)
```

100% instruction and branch coverage is enforced.
