# 0055. Parity tables are reasoned about jointly, as a linear system over GF(2)

**Status**: Accepted

## Context

`pret-150-40` (XCSP3 `Pret` family: 150 boolean variables, 100 ternary extension tables) did not
finish in 30s: 17M nodes, the search still at 100% unexplored. Every table lists exactly the tuples
`(0,0,1)(0,1,0)(1,0,0)(1,1,1)`, which is `x ⊕ y ⊕ z = 1`, so the instance is a linear system over
GF(2) and is unsatisfiable because the system is inconsistent. Search over such a system is
exponential in the number of rows it needs to combine; Gaussian elimination decides it in
polynomial time. Table propagation sees one row at a time and cannot combine two.

## Decision

`ParitySystemConstraint` holds a group of parity rows and propagates them by Gauss-Jordan
elimination over the variables still open, folding assigned variables into the right-hand sides. A
row reduced to `0 = 1` is a contradiction; a row left with one variable pins it. It accepts
`Boolean` and 0/1 integer variables, alike or mixed in one row.

`ParitySystems` finds the rows at the **model level**, in the `solver.redundant` package, which
`Solver.Factory` applies before either chain is built through `RedundantConstraints`, so every front
end gets it rather than only the XCSP3 parser. A table is a parity row when
its tuples are exactly the `2^(k-1)` tuples of one parity over `k` distinct variables, holding only
`0`/`1` or `false`/`true`; a conflict table of the opposite parity is the same row. Rows sharing
variables form a group, and a group is posted when it has 2 to 512 rows and at least one row of three
or more variables (a group of pairs is a set of equalities and disequalities, which arc consistency
already handles).

The system is **redundant**: the tables stay and a solution must still satisfy them. The solver
works on the augmented copy and the caller's problem is not changed, because the problem model
carries no solver concept ([ADR-0050](0050-the-objective-cut-is-an-ordinary-constraint.md)). Doing it
before the chain is built also lets `FixpointPropagation.Factory#forProblem`, which filters
propagators by the constraint types present, see the new type.

No `explainInfeasible` override: the default (no explanation, the caller cites the full assignment)
is sound, and a tighter citation would need the rows of the combination that failed.

## Measurements

`pret-150-40`: timed out at 17M nodes before, proved UNSAT at the root with 0 nodes now. The
bundled corpus is unchanged (83 solved, 21 optima, no status flips; 357.2s against 357.9s). The
`sat` folder goes from 14 to 15 of 18 solved.

## Rejected alternatives

- **Recognise rows only in the XCSP3 parser.** Smaller, but builder users and any other front end
  would never benefit, and `Boolean` tables could not use it.
- **One propagator per row.** What the tables already are; it cannot combine rows, which is the
  whole point.
- **Replace the tables with the system.** Loses the per-row table propagation and makes solution
  checking depend on the recognition being right. Keeping them costs one extra propagator.
- **No size limit.** Each propagation re-eliminates the whole system, `rows² × columns / 64` word
  operations, so a very large group would cost more per node than it saves. The 512-row cap bounds it.
  A system that is kept incrementally reduced between nodes would lift the cap and is not done.
