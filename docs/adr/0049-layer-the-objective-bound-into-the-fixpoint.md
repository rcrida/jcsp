# 0049. Layer the objective bound into the fixpoint, instead of narrowing with it outside

**Status**: Accepted

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

Carry the objective bound as a layered constraint on the problem, the way nogoods are carried, and
delete the hand-run pass:

1. Give `ConstraintSatisfactionProblem` a second layered slot beside `nogoods` — a single
   `@Nullable LinearBoundConstraint<Integer> objectiveBound`, set by a `withObjectiveBound(...)` that
   reuses `constraintGraph.getConstraints()` and the constructor's reference-equality fast path
   exactly as `withNogoods` does. A separate typed slot rather than widening `nogoods` to
   `Set<Constraint>`, so `NogoodFixpointConsistency`'s typed by-variable index and `nogoodsLearned`
   accounting stay as they are.
2. Include it in the cached union behind `getConstraints()` and in `getConstraintsTouching`, and key
   the merge cache on it as well as on the nogood snapshot reference.
3. Have `BranchAndBoundSolver` replace the bound as the incumbent improves and drop
   `ObjectiveCut.narrow`/`ObjectiveCut.Narrowed`. The fixpoint then propagates the bound like any other
   constraint, with ADR-0019's dirty tracking applying unmodified.
4. Declare the bound's scope to the node's inference **exactly when that node takes on a bound the
   problem was not already carrying**, via ADR-0047's `alsoChanged`. This is the one point where the
   proposal was wrong and the implementation differs — see *What building it corrected* below.
5. Discard any bound the problem arrives carrying, in `getSolutions`, for the reason
   [ADR-0048](0048-a-bound-conditional-nogood-lives-for-one-search.md) empties the nogood store: a
   bound is relative to the incumbent that justified it, a solve starts unbounded, and
   `withObjectiveBound` is public API, so a caller can hand one in. Keeping it could prune away the
   solve's own optimum.
6. Build the per-solve propagator list as though the bound were present, since it is added after
   `FixpointPropagation.Factory.forProblem` has filtered on the types in the problem.
   `ObjectiveCut.isExpressible` already answers exactly the question that gate needs ("can this
   objective be cut at all"), once per solve.
7. Keep the bound out of `equals`/`hashCode`, as nogoods are: it is derived from the incumbent, not
   part of the problem's identity.

`Inference`'s `alsoChanged` overloads stay, and are still used — by this ADR's own step 4. What
changes is what gets declared and how often: ADR-0047 declared, per node, whatever a hand-run pass had
just narrowed; this declares a fixed, precomputed scope only at the nodes that take a new bound on.

## What building it corrected

Three claims in the first draft of this ADR were wrong, and they are worth keeping because each one
looked obviously true:

- **"The declaration becomes unnecessary."** It does not. Both levels of the fixpoint are woken by
  changed *variables* — `FixpointConsistency`'s inner `ConstraintQueue` seeds from the dirty set, and
  `FixpointPropagation`'s `Coverage` index is memoized against the constraint graph that layering
  deliberately leaves untouched — so a constraint that is *new to the problem* is in no diff the
  machinery can take for itself. Adding a constraint is itself an undeclared change. Closing that
  properly would mean teaching the worklist about constraints it has not yet propagated, which is a
  separate change to ADR-0019's machinery; until then, the invisible-mutation class is closed for
  *domains* and still open for *constraints*.
- **"So declare it at every node."** The correction to the correction, and the one that decides whether
  this ADR pays at all. The bound reads nothing but its own variables, so re-propagating it where none
  of them has moved cannot narrow anything, while the dirty-driven mechanism already visits it whenever
  one of them does. Declaring per node buys nothing and costs an O(scope) propagation per node:
  measured at 22 optima / 852.5s, against 23 / 797.5s for declaring only on change. The per-node
  version was *slower than the hand-run pass it replaced*, which is what made the design look like a
  dead end before the policy was fixed.
- **"The bound is fresher than the hand-run pass."** It is not. A node applies the bound once, on
  entry, so an improvement found deeper does not tighten that node's constraint for its remaining
  sibling candidates; each child re-applies the current bound when it is entered. That is exactly the
  freshness the per-node `narrow` had, since it also computed its cut on entry. The declaration is
  therefore paid once per *candidate* at a node that takes a bound on, not once per improving solution.

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
- **Leaving ADR-0047's declaration as the permanent answer.** It works and it was committed. It leaves
  the invariant enforceable only by remembering to honour it, leaves branch-and-bound propagating its
  bound once per node by hand where every other constraint goes to fixpoint, and — as it turned out —
  costs an optimality proof this ADR gets back.
- **Widening `nogoods` to a general layered-constraint set.** More uniform, and the better shape if a
  third layered kind ever appears. Not worth disturbing `NogoodFixpointConsistency`'s index and the
  nogood statistics for one extra constraint.

## Measurement

All 85 bundled instances via `Xcsp3CompetitionRunner`, 60s each, seed 20260830, with every instance
that moved re-run at seeds 7 and 99. The two reference arms are the ones
[ADR-0047](0047-declare-caller-narrowed-domains-to-the-inference.md) records.

| arm | optimum found | total solve |
|---|---|---|
| before ADR-0047: narrow, nothing declared | 23 of 31 | 773.9s |
| ADR-0047 as committed: narrow, declared per node | 22 of 31 | 831.8s |
| **this ADR: layered, declared on change** | **23 of 31** | **797.5s** |
| this ADR with the per-node declaration | 22 of 31 | 852.5s |

Zero result changes against the pre-ADR-0047 baseline, and no `SolutionChecker` failure in any arm.
Against the committed arm this is one more optimality proof and 4.1% less time; against the baseline it
is the same 23 proofs for 3.1% more time, with the work redistributed:

| instance | baseline | this ADR | across 3 seeds |
|---|---|---|---|
| `PrizeCollecting-15-3-5-0` | OPT 4.70s, 10.6k nodes | **OPT 3.68s, 5.4k** | 11k/15k/12k → 5k/9k/7k |
| `Taillard-os-04-04-0` | OPT 17.9s, 112k | **OPT 4.1s, 21k** | 112k/133k/76k → 21k/9k/9k |
| `GolombRuler-09-a4` | OPT 25.2s, 648k | OPT 44.4s, 1,502k | 648k/643k/644k → 1502k/1450k/1535k |
| `Fastfood-ff10` | OPT 7.1s, 49k | OPT 12.1s, 101k | 49k/50k/50k → 101k/97k/97k |
| `TravellingSalesman-20-30-00` | SAT **o 118**, 2,226k | SAT **o 124**, 1,519k | fewer nodes every seed |
| `Vrp-P-n16-k8` | SAT o 455, 2,113k | SAT o 455, 244k | 2113k/2134k/2068k → 244k/224k/219k |

So it recovers the proof ADR-0047 cost *and* beats the baseline on that instance, and keeps ADR-0047's
`Taillard-os-04-04-0` win. Three things are worse and reproduce on every seed: `GolombRuler-09-a4`
(+76% time, unchanged from the committed arm, consistent with ADR-0047's finding that this one is the
dom/wdeg feedback loop rather than anything about how the bound is applied), `Fastfood-ff10` (+69%),
and `TravellingSalesman-20-30-00`'s objective inside the budget — 118 → 124, with a third fewer nodes,
which is the clearest single case of "more inference per node is not the same as a better search".
`Vrp-P-n16-k8` and `Vrp-A-n32-k5` reach identical objectives on a fraction of the nodes: neither a win
nor a loss, just slower, stronger nodes.

## Consequences

- The invisible-mutation class is closed **for domains**: no caller narrows domains between
  propagation passes any more. It remains open for constraints, since adding one is itself an
  undeclared change — see *What building it corrected*.
- The bound is a constraint like any other: it appears in `getConstraints()`, in
  `getConstraintsTouching`, and in the fixpoint, where it is re-propagated against each node's own
  narrowed domains and compounds with every other propagator. That is why a thin bound that narrows
  nothing at the root can still bite deeper down, the property ADR-0044 relies on for its probes.
- **`ConstraintSatisfactionProblem` now holds a solver concept.** The problem model has no objective —
  that is supplied separately to `createSolver` — yet it carries an `objectiveBound` typed to
  `LinearBoundConstraint<Integer>`. This is the real debt this ADR takes on, and the honest place to
  pay it is a general layered-constraint slot (listed as a rejected alternative above, on grounds that
  look weaker now that there is a second layered kind in the code).
- It invalidates a documented assumption in the hottest code. `FixpointConsistency.filterCache` keys on
  `csp.getConstraints()`'s reference and its Javadoc rests on "`source` is fixed at CSP-build time and
  never mutates mid-solve". With learning off — the default — the nogood set is always empty, so that
  held; a bound that changes per improving solution makes every propagator entry re-filter and rebuild
  its `indicesByVariable` at that point. Per improvement, not per node, and the merge cache keeps the
  reference stable in between, which is why it is affordable.
- `ObjectiveCut` shrinks to what `BoundedFirstSolution` still needs — `constraintFor`, `enforce` and
  `isExpressible` — and the `narrow`/`Narrowed` pair is gone.
- What this obligates: the declaration policy is load-bearing and counter-intuitive, so any future
  change to *when* the scope is declared owes the same four-arm run. The figures above are the
  comparison to beat, and `GolombRuler-09-a4`/`Fastfood-ff10`/`TravellingSalesman-20-30-00` are the
  three instances to look at first.
