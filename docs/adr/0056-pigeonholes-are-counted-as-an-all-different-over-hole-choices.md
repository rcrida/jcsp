# 0056. Pigeonholes are counted as an all-different over hole choices

**Status**: Accepted

## Context

`hole-09` and `hole-10` (XCSP3 `Hole` family: ten pigeons, nine holes, and eleven, ten) did not
finish in 30s: millions of nodes, the search still at 99.99% unexplored. Each pigeon is a table
forbidding the all-zero tuple (the clause "some hole"), and each pair of pigeons in a hole is a table
forbidding `(1,1)`. The instances are unsatisfiable by counting. That is exponential for resolution,
so for clause learning too, and no heuristic or restart schedule changes it. Table propagation sees
one table at a time and never counts.

## Decision

`PigeonholeConstraint` holds pigeons (variable sets of which at least one is true) and holes
(variable sets of which at most one is). Pigeons share no variable, so the variable that satisfies a
pigeon is its own, and two pigeons satisfied through one hole would make two variables of it true:
the pigeons need distinct holes. `propagate` gives each pigeon the holes of its still-possible
variables (or only those of a variable already true) as a domain and runs `AllDiffConstraint`'s
Régin propagation over those choices, reusing its matching and Hall-set reasoning. No matching
exists: infeasible. A variable none of whose holes its pigeon can still use is set false, which is
sound because a pigeon satisfied through that variable would use one of them.

`Pigeonholes` finds them at the model level, from forbidden all-false tables, `AtMostOneConstraint`
(and `ExactlyOneConstraint`, which extends it) and cliques grown greedily from forbidden `(1,1)`
pair tables. It runs with `ParitySystems` under `RedundantConstraints`, in the `solver.redundant`
package, which `Solver.Factory` applies to both chains for the reasons given in
[ADR-0055](0055-parity-tables-are-reasoned-about-jointly-as-a-gf2-system.md). The constraint is
redundant: the clauses and at-most-one constraints stay.

A group of pigeons is posted when it has 2 to 1,024 pigeons and **no more holes than pigeons**.
That last condition is a cost gate, not a soundness one. A graph-colouring encoding has a pigeon
per node and a hole per edge and colour, many more holes than pigeons, so the matching would run at
every node and refute nothing. Clauses sharing a variable are dropped greedily in name order, and a
clause is a pigeon only if its variables have bit-only domains, since a clause is satisfied by any
value but 0 and the counting treats that as "true".

## Measurements

`hole-09` and `hole-10`: timed out before, proved UNSAT at the root with 0 nodes now. The bundled
corpus is unchanged (83 solved, 21 optima, no status flips; 358.4s against 357.2s). The `sat` folder
goes from 15 to 17 of 18 solved. The graph-colouring `Sat-flat200` instances are unchanged.

## Rejected alternatives

- **Symmetry breaking over interchangeable pigeons.** The other standard cure, but it needs the
  symmetry detected first, which is harder than the matching and no more general here.
- **An LP relaxation.** Summing the pigeons' clauses against the holes' at-most-one constraints is
  infeasible, but only over the cliques, which have to be found first anyway; pairwise `x + y <= 1`
  alone leaves the relaxation feasible at 1/2.
- **Posting every group.** Measured nothing on the colouring instances and costs a matching per node.
- **A bespoke matching.** `AllDiffConstraint` already is one, tested, with Hall-set pruning.
