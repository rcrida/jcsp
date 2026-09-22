# ADR-0029: Apply the incumbent as a propagated constraint, not only as a branch cut

**Status:** Accepted (2026-09-22)

## Context

`BranchAndBoundSolver` used the incumbent in exactly two places, both of them predicates in the
search loop: `bound.lowerBound() >= incumbent` (the LP relaxation, ADR-0009) and
`objective.applyAsDouble(partial) >= incumbent` (the plain fallback). Neither removes a value from
any domain. `LpModelBuilder.solve` did not even take the incumbent as a parameter.

The consequence is that the incumbent was invisible to propagation. However good the bound got,
`AllDiffConstraint`, `GlobalCardinalityConstraint`, the sum propagators and everything else in the
fixpoint carried on as though no solution had ever been found. Each improvement pruned the node it
was tested at, and nothing more.

This was found by asking why Choco emits fewer intermediate solutions than jcsp on
`Fastfood-ff10`, and reading both implementations. Choco's `AbstractIntObjManager.postDynamicCut`
is:

```java
objective.updateUpperBound(cutComputer.applyAsInt(bestProvedUB), this);
objective.updateLowerBound(bestProvedLB, this);
```

The objective is a real `IntVar` and `this` is an `ICause`, so every new solution is a genuine
domain modification that propagates through the constraint linking the objective to the decision
variables. That is the asymmetry: Choco's incumbent is information the whole fixpoint consumes,
jcsp's was a comparison.

## Decision

Narrow domains at each node by the objective cut `sum(c_v * v) <= incumbent - constant - 1`,
before branching, letting the child node's ordinary inference fixpoint amplify it.

No objective variable is needed, and none is introduced: that cut *is* a `LinearBoundConstraint`,
which jcsp already propagates (including ADR-0023's subset-sum coverage). The constraint is cached
in a single slot keyed on the incumbent it was built for, since the incumbent changes only on an
improvement while the cut is consulted at every node.

Sound because it removes only assignments whose cost is at least the incumbent, and those are by
definition not improving -- the only thing `getSolutions` promises to emit.

Restricted to a wholly integral objective over discrete domains. A fractional coefficient or
constant would need the strict `< incumbent` loosened by an epsilon to stay sound, and a
`BoundedDomain` anywhere in the objective disqualifies it for two coinciding reasons: `-1` is not
the next representable improvement over a continuous cost, and an `Integer`-bounded
`LinearBoundConstraint` dispatches to integer propagation, which cannot read a continuous domain.
Where it does not apply the cut is skipped entirely rather than approximated.

## Consequences

Interleaved A/B, fixed seeds, identical objective values throughout:

| Instance | Before | After | |
|---|---|---|---|
| `testObjective1` | 30s timeout, 22,580n, `SATISFIABLE` | **2.3s, 2,034n, `OPTIMUM FOUND`** | closes the proof |
| `Opd-07-007-003` | 6.17s, 53,602n | **2.55s, 19,150n** | 2.4x |
| `PrizeCollecting-15-3-5-0` | 14.0s / 15.0s | **8.6s / 4.4s** | 1.6-3.4x |
| `Fastfood-ff10` | 389,824n @60s | 329,624n @60s | -18% nodes |
| `Mario-easy-4`, `Knapsack-30-100-00` | — | — | neutral |

`testObjective1` is the clearest statement of what the cut does. Both arms find cost 11; only the
cut arm can *prove* it optimal. Proving optimality means exhausting the space under
`cost < incumbent`, which is exactly the region the cut prunes -- so the mechanism bites hardest
on the proof, not on the search for a first good solution.

Whole-corpus sweep, 30s: zero `SolutionChecker` mismatches, zero failures, no instance lost, and
`testObjective1` gained. `Opd-07-007-003` was one of the instances the Choco head-to-head put at
roughly 250x behind.

**The motivating prediction was wrong, and the change is worth having anyway.** The reasoning that
led here was that propagating the incumbent would make jcsp take bigger steps between solutions,
explaining Choco's 29 improving solutions against jcsp's 47 on `Fastfood-ff10`. It does not:
after the change jcsp still emits exactly 47, with identical values. The cut prunes subtrees that
cannot contain a better solution; it does not steer search toward better ones. Choco's shorter
sequence must come from search order instead -- its first solution is 2243 against jcsp's 3312 --
and that remains unexplained.

## Rejected alternatives

- **Adding `sum(c*x) <= incumbent - 1` as a row in the LP relaxation.** Worthless, and provably so
  rather than by measurement: the LP minimizes that same expression, so an upper bound on it
  cannot raise the minimum. The LP is infeasible exactly when `LP* > incumbent - 1`, which is the
  `lowerBound() >= incumbent` test already performed. Identical pruning, extra row.
- **Materialising an objective variable plus a linking `LinearVariableConstraint`**, the literal
  analogue of Choco's `IntVar`. Rejected as strictly more invasive for the same effect: a new
  variable changes `isComplete`, the reported search space and the variable selector's view, and
  the linking constraint propagates to exactly the same place the cut constraint does directly.
- **Restart-on-solution**, tried first on the same "Choco does X" reasoning. Built, measured,
  reverted -- see [ADR-0028](0028-restart-on-solution-rejected-for-branch-and-bound.md). Worth
  contrasting: that change was an analogy to Choco's behaviour, this one came from reading what
  Choco's code actually does and finding a mechanism jcsp genuinely lacked.
