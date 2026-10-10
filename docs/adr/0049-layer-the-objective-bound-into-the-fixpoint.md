# 0049. Layer the objective bound into the fixpoint, instead of narrowing with it outside

**Status**: Proposed

## Context

Branch-and-bound's incumbent bound reaches the domains by a route no other constraint uses. At every
node, `ObjectiveCut.narrow` builds `Σ cᵢ·xᵢ ≤ C−1`, runs *one* propagation pass of it by hand, and
hands the narrowed problem on ([ADR-0029](0029-objective-cut-as-a-propagated-constraint.md)). The
constraint itself is never part of the problem.

Two consequences follow from that, and both have now cost something:

- **The narrowing is invisible to the propagation machinery.** It happens outside any pass, so it
  appears in no diff the next pass can take, and wakes no propagator.
  [ADR-0047](0047-declare-caller-narrowed-domains-to-the-inference.md) fixes that by having the
  caller *declare* what it narrowed, through a new `alsoChanged` parameter on `Inference`. That works,
  but it is a patch: the rule every other part of the system relies on — ADR-0024's worklist,
  ADR-0019's dirty tracking, `propagationInference`'s seed — is "every domain change is declared", and
  this is the one caller that can break it by omission. The next caller that narrows domains between
  passes (a dominance rule, a symmetry breaker, a second relaxation's bound) inherits the same trap.
- **One hand-run pass is weaker than a constraint.** A bound spread thinly across many objective
  variables narrows nothing on a single pass and is then gone, which is exactly why
  [ADR-0044](0044-bounded-probes-for-the-starting-incumbent.md)'s probes `enforce` their bound as a
  real constraint instead. Branch-and-bound cannot do the same today, because ADR-0029 measured
  per-node constraint addition as too expensive: it rebuilds the `ConstraintGraph` and re-filters the
  propagator list.

That cost argument is sound for *adding a constraint per node* — and it is not the only way to put a
constraint in the problem. This codebase already has a mechanism for carrying a constraint that
changes during search without touching the graph: `ConstraintSatisfactionProblem.withNogoods`
layers learned nogoods on top of an untouched `constraintGraph`, with a reference-equality fast path
and a merge cache so the union behind `getConstraints()` is rebuilt only when the layered set actually
changes ([ADR-0002](0002-nogood-learning-as-first-class-constraints.md)). The objective bound has the
same shape as a nogood in every respect that mechanism cares about: derived rather than declared,
changing rarely (only when the incumbent improves), and contributing nothing to neighbours, binary
decomposition or cycle analysis.

## Decision

*Proposed, not implemented.* Carry the objective bound as a layered constraint on the problem, the way
nogoods are carried, and delete the hand-run pass:

1. Give `ConstraintSatisfactionProblem` a second layered slot beside `nogoods` — a single
   `@Nullable LinearBoundConstraint<Integer> objectiveBound`, set by a `withObjectiveBound(...)` that
   reuses `constraintGraph.getConstraints()` and the constructor's reference-equality fast path
   exactly as `withNogoods` does. A separate typed slot rather than widening `nogoods` to
   `Set<Constraint>`, so `NogoodFixpointConsistency`'s typed by-variable index and `nogoodsLearned`
   accounting stay as they are.
2. Include it in the cached union behind `getConstraints()` and in `getConstraintsTouching`, and key
   the merge cache on it as well as on the nogood snapshot reference.
3. Have `BranchAndBoundSolver` call `withObjectiveBound` only when the incumbent improves — once per
   improving solution, not once per node — and drop `ObjectiveCut.narrow`, `ObjectiveCut.Narrowed` and
   the `alsoChanged` argument at its own call site. The fixpoint then propagates the bound like any
   other constraint, with the worklist waking it whenever one of its variables changes and ADR-0019's
   dirty tracking applying unmodified.
4. Build the per-solve propagator list as though the bound were present, since it is added after
   `FixpointPropagation.Factory.forProblem` has filtered on the types in the problem.
   `ObjectiveCut.isExpressible` already answers exactly the question that gate needs ("can this
   objective be cut at all"), once per solve.
5. Keep the bound out of `equals`/`hashCode`, as nogoods are: it is derived from the incumbent, not
   part of the problem's identity.

`Inference`'s `alsoChanged` overloads stay regardless. They are the general answer for any caller that
narrows domains outside a pass, and this proposal removes the only caller that needs them today —
which is the point: the trap closes because nothing is narrowing behind the fixpoint's back any more.

## Rejected alternatives

- **A constraint with a mutable bound, added once at the root.** The cheapest-sounding version: no
  per-improvement object at all, just a field the incumbent writes. Rejected on the object model —
  constraints and domains are immutable records ([ADR-0007](0007-record-based-domain-object-model.md)),
  caches key on reference identity, and a constraint whose propagation result silently changes under a
  cached CSP is the kind of thing those caches cannot defend against. A fresh constraint per
  improvement costs one small allocation at a rate measured in solutions, not nodes.
- **Adding the constraint through `toBuilder()` per improvement.** Correct and far simpler, but it
  rebuilds the `ConstraintGraph` and forces the `@Singular` builder to rebuild the whole constraint
  set — the cost ADR-0029 rejected. Per improvement rather than per node it might even be affordable;
  the layered slot avoids the question entirely and reuses a path already profiled for exactly this.
- **Leaving ADR-0047's declaration as the permanent answer.** It works and it is committed. It just
  leaves the invariant enforceable only by remembering to honour it, and leaves branch-and-bound
  propagating its bound once per node by hand where every other constraint is propagated to fixpoint.
- **Widening `nogoods` to a general layered-constraint set.** More uniform, and the better shape if a
  third layered kind ever appears. Not worth disturbing `NogoodFixpointConsistency`'s index and the
  nogood statistics for one extra constraint.

## Consequences

- Closes the invisible-mutation class rather than patching its one instance: after this, no caller
  narrows domains between propagation passes, so the "every change is declared" rule holds by
  construction instead of by discipline.
- The bound gets re-propagated at every node against that node's narrowed domains, compounding with
  other propagators to fixpoint, instead of once per node by hand. That is strictly stronger
  inference, so a thin bound that narrows nothing at the root can still bite deeper down — the
  property ADR-0044 relies on for its probes.
- **It is not a fix for the search-order trade** ADR-0047 measured. Stronger propagation perturbs
  dom/wdeg's feedback loop the same way, probably more; `PrizeCollecting-15-3-5-0`'s lost proof may
  well persist or worsen. Anyone implementing this must re-run the four-arm corpus measurement in
  ADR-0047 and compare against the 23-optima / 773.9s baseline recorded there, not against its own
  intuition. If it loses more than ADR-0047 did, the architectural win has to be argued on its own
  terms or the whole line abandoned.
- The risk sits in the hottest code in the system. `ConstraintSatisfactionProblem`'s constructor fast
  paths and the nogood merge cache were tuned against JFR profiles of real instances
  (`Steiner3-08`), and a second layered slot touches both. The work is small; the measurement
  obligation around it is not.
- `ObjectiveCut` shrinks to what `BoundedFirstSolution` still needs — `constraintFor`, `enforce` and
  `isExpressible` — and the `narrow`/`Narrowed` pair goes away, along with its single-slot cache.
