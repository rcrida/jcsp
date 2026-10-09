# 0048. A bound-conditional nogood lives for one search

**Status**: Accepted

## Context

[ADR-0002](0002-nogood-learning-as-first-class-constraints.md) models a learned nogood as a real
propagatable constraint, on the grounds that it records "a genuine constraint violation (permanently
true regardless of the incumbent)" — which is what lets a `NogoodStore` be shared across Luby
restarts and, in `BranchAndBoundSolver`, across a whole branch-and-bound search.

In branch-and-bound that premise does not hold. Every node propagates against the problem
[ADR-0029](0029-objective-cut-as-a-propagated-constraint.md)'s objective cut has narrowed, so a
wipeout can be caused by the bound rather than by the problem's own constraints, and the reason
recorded for it cites only the assignment (`GroundNogoodConstraint`) or the current domains
(`ValueSetNogoodConstraint.fromCurrentState`) — never the bound those domains came from. The nogood
therefore means "this prefix fails *while costing less than the incumbent*".

That is sound for the rest of one search, where the incumbent only ever tightens: a nogood derived
under bound *B* still holds under any *B' ≤ B*. It is not sound across searches. `nogoodStore` is a
per-solver field, while `getSolutions` starts the incumbent back at `Double.MAX_VALUE`, so a second
solve on the same solver inherited nogoods recorded under the first solve's optimum — including ones
derived above the prefix of that optimum itself, which the first search had cut as "no better than
what we hold". The second solve could then only return something strictly worse, or nothing.
`BoundSolver.getSolution()` consumes `getSolutions()`, so the two in sequence reproduced it.

## Decision

Branch-and-bound's nogood store is per-solve state, emptied at the start of each `getSolutions()`
call, alongside the variable selector and the LP gate that are already rebuilt there for the same
reason. `NogoodStore.clear()` exists for that, and its Javadoc says which kind of owner needs it.

`DomWdegLubySearch`'s store is untouched. With no bound of its own, its nogoods are unconditional
facts about the problem, which is the case ADR-0002 describes.

## Rejected alternatives

- **Derive the reasons from the un-cut problem**, so they are unconditional and may be kept. The cut
  is folded into the problem before the node's inference runs, and inference is a single pass that
  explains the failure it finds ([ADR-0002](0002-nogood-learning-as-first-class-constraints.md)); a
  second pass over un-cut domains, at every failing node, to re-derive a reason that may not exist
  there at all, is the "separate from-scratch re-derivation" that design exists to avoid.
- **Record each nogood with the bound it was derived under**, and admit it only while the incumbent
  is at least as tight. This is the honest general answer, and it is what a CDCL solver's assumption
  levels do. It costs a field per nogood, a check per propagation, and the eviction policy has to
  reason about it — all to retain facts across a boundary that
  [ADR-0030](0030-nogood-learning-is-not-cdcl.md) already shows barely pays within one search, where
  this store's nogoods almost never fire.
- **Leave it, and document that a solver is single-use.** Nothing else in the chain is: both
  `BoundSolver` entry points are callable repeatedly, the satisfaction chain's are, and the failure
  mode here is a wrong answer (a worse "optimum", reported as optimal) rather than an error.

## Consequences

- Repeated solves from one optimization solver are independent, and each one's drained stream is a
  proof of optimality for that solve.
- Learning is not carried between solves of the same problem. Nothing in the chain did carry it
  usefully: nogoods are per-problem and, per ADR-0030, rarely fire here at all.
- Anything that later gives branch-and-bound a bound-independent source of nogoods (or tags them with
  their bound) can revisit this; the obligation is on that change to say why its nogoods are
  unconditional.
- A future search that prunes by a bound of its own and shares a store inherits the same question.
  `NogoodStore.clear()`'s Javadoc states the rule it has to answer: a nogood is only a fact if
  nothing but the problem's own constraints produced it.
