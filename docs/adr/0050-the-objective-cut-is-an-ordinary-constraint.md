# 0050. The objective cut is an ordinary constraint, not a layer on the problem

**Status**: Accepted

## Context

[ADR-0049](0049-layer-the-objective-bound-into-the-fixpoint.md) put branch-and-bound's incumbent
bound onto a second layered slot on `ConstraintSatisfactionProblem`, beside `nogoods`, so the bound
would be propagated by the ordinary fixpoint instead of by a hand-run pass. It worked, and its own
Consequences recorded the debt it took on: the problem model has no objective, yet it carried a slot
named for one, typed `LinearBoundConstraint<Integer>`. The fix it proposed was to generalise that slot
into a domain-neutral layered-constraint slot.

Three things say generalisation was the wrong fix.

**A nogood and a cut have different logical status.** A learned nogood is *entailed* by the problem's
own constraints: adding it leaves the solution set identical and only makes the search discover that
sooner. That is what licenses [ADR-0002](0002-nogood-learning-as-first-class-constraints.md) treating
nogoods as derived knowledge, excluded from the problem's identity. An objective cut is not entailed
by anything in the problem — it follows from what the search has found, and it strictly removes real,
feasible solutions, every one costing at or above the incumbent. The problem carrying a cut genuinely
*is* a more constrained problem, so "a more constrained sub-problem, solved as normal" is the honest
model for it, and the layering-as-derived-knowledge model fits only nogoods.

**The frequency argument does not transfer.** Layering exists to avoid `ConstraintGraph` rebuilds at
*nogood* frequency — one per failing node, per ADR-0002. A cut changes once per *improving solution*: a
handful to a few dozen per solve. ADR-0029 was right that a rebuild per node is unaffordable and wrong
to carry that conclusion to a bound that moves orders of magnitude less often.

**Layering's safety story was weaker than ADR-0049 claimed.** That ADR said a layered n-ary constraint
"contributes nothing to neighbours, binary decomposition or cycle analysis". `ConstraintGraph` computes
neighbours from `getVariables()` of *every* constraint regardless of arity, so **any** layered
constraint spanning two or more variables leaves `getNeighbours`/`isCyclic`/`isFullyConnected`/
`isTree` **stale**, not unaffected. A general slot would have widened that: it would accept a
`BinaryConstraint`, whose arcs live only in the graph, so AC3 would never revise it and a problem could
be misclassified as a tree, routed to `TreeSolver`, and answered from a relaxation. Paying the debt by
generalisation therefore meant adding a runtime guard and documenting three pre-existing layer-leak
sites (`decomposeSubproblems`, `withVariableSubset`, `computeRestrictedNeighbours`), where paying it by
deletion makes all of that moot.

## Decision

Branch-and-bound puts its cut in the problem's own constraint set and replaces it there as the
incumbent improves. `ConstraintSatisfactionProblem` goes back to one layer, `nogoods`, and carries no
solver concept.

1. A private `Bound` record in `BranchAndBoundSolver` holds the solve's cut-free structural constraint
   set plus whichever cut is currently applied, and is threaded down the recursion. `tightenedTo`
   returns the same instance when nothing changed — reference equality against `ObjectiveCut`'s
   per-bound cache — and `applyTo` rebuilds the problem as `structural + cut`.
2. That state lives in the **search**, not on the problem. It is the O(1) answer to "is the current cut
   already applied" that the layered slot used to give; scanning the constraint set per node would be
   O(constraints).
3. Replacement, not accumulation. Each new cut strictly subsumes the last, so accumulating would
   propagate k dead constraints per node after k improvements. Applying on top of the fixed structural
   baseline replaces in one step without having to find and remove the old one.
4. ADR-0047's `alsoChanged` declaration stays, and stays on the same policy: the cut's scope is
   declared to the inference exactly at the nodes that take a new cut on. Both fixpoint queues are
   seeded by changed *variables*, so a constraint that was not in the problem a moment ago is in no
   diff the machinery can take, however it got there. ADR-0049's measurement of the alternative holds:
   declaring per node costs an O(scope) propagation that derives nothing, and measured 22 optima /
   852.5s against 23 / 797.5s.
5. `ConstraintSatisfactionProblem.withObjectiveBound`, the `objectiveBound` slot, its assert, its
   `getConstraintsTouching` branch and its merge-cache key are deleted; `mergedWithLayers` reverts to
   `mergedWithNogoods`.

## Rejected alternatives

- **Generalising the slot** to `withSearchConstraint`/`getSearchConstraint` typed `Constraint`, which
  is what ADR-0049 proposed and what this work set out to do. It pays the naming debt but keeps the
  mechanism, needs a layerability guard precisely because the type no longer enforces safety, leaves
  the three layer-leak sites to document, and measured 2.0% slower than deletion. Keeping a mechanism
  in order to rename it is the wrong trade when the mechanism itself is removable.
- **Accumulating cuts** rather than replacing. Simpler to write — no structural baseline — but every
  superseded cut stays in the set and is propagated at every subsequent node while being unable to
  prune anything the tightest cut doesn't.
- **A constraint with a mutable bound**, added once at the root. Rejected on the object model, as in
  ADR-0049: constraints are immutable records ([ADR-0007](0007-record-based-domain-object-model.md)) and
  caches key on reference identity, so a constraint whose propagation result changes under a cached CSP
  is undefendable. A fresh constraint per improvement costs one allocation at solution frequency.
- **Reusing `ObjectiveCut.enforce`** as-is for branch-and-bound. It adds rather than replaces, which is
  all a probe needs, since a probe's bound is fixed for its whole search.

## Measurement

`Xcsp3CompetitionRunner`, all 85 bundled instances, 60s each, seed 20260830, with every instance that
moved re-run at seeds 7 and 99.

| arm | optimum found | total solve |
|---|---|---|
| before ADR-0047 (hand-run narrow, nothing declared) | 23 of 31 | 773.9s |
| ADR-0047 as committed (narrow, declared per node) | 22 of 31 | 831.8s |
| ADR-0049 (layered slot, declared on change) | 23 of 31 | 797.5s |
| **this ADR (ordinary constraint, declared on change)** | **23 of 31** | **781.9s** |

Zero result changes against *either* reference arm, and no `SolutionChecker` failure anywhere. So the
per-improvement rebuild — which discards not just the graph but every memo cached against it
(`TouchingIndex`, `Coverage`, each propagator's `FilterCache`, AC3's arc index) and re-runs
`validateConstraints` — costs about 1% of total solve time at this frequency.

Against the pre-ADR-0047 baseline, with three-seed figures:

| instance | baseline | this ADR | across 3 seeds |
|---|---|---|---|
| `PrizeCollecting-15-3-5-0` | OPT 4.70s, 10.6k | **OPT 3.05s, 5.5k** | 11k/15k/12k → 6k/9k/7k |
| `Taillard-os-04-04-0` | OPT 17.9s, 112k | **OPT 3.61s, 21k** | 112k/133k/76k → 21k/9k/9k |
| `TravellingSalesman-20-30-00` | SAT **o 118**, 2,226k | SAT **o 112**, 1,186k | 2226k/2180k/2239k → 1186k/1201k/1173k |
| `Vrp-P-n16-k8` | SAT o 455, 2,113k | SAT o 455, 844k | 2113k/2134k/2068k → 844k/842k/840k |
| `GolombRuler-09-a4` | OPT 25.2s, 648k | OPT 40.4s, 1,502k | 648k/643k/644k → 1502k/1450k/1535k |
| `Fastfood-ff10` | OPT 7.1s, 49k | OPT 9.3s, 56k | 49k/50k/50k → 56k/57k/57k |

It also repairs what ADR-0049 cost on top of the baseline: `Fastfood-ff10` 101k → 56k nodes,
`QuadraticAssignment-qap` 6.4k → 2.6k (now better than baseline), `LowAutocorrelation-015` back under
baseline. `TravellingSalesman-20-30-00`'s objective is the clearest single result — 112 against the
baseline's 118 and ADR-0049's 124, the best figure this solver has produced on that instance.

`GolombRuler-09-a4` is the one instance left worse than baseline, and its node counts are *identical*
to ADR-0049's on all three seeds (1502k/1450k/1535k), which confirms what ADR-0047 concluded: that
regression is dom/wdeg's conflict-weight feedback loop reacting to stronger propagation, and nothing to
do with how the bound reaches the problem.

## Consequences

- `ConstraintSatisfactionProblem` holds exactly one derived layer again, `nogoods`, whose entailed
  status is what makes layering sound for it. The debt ADR-0049 recorded is paid by removal.
- **The inherited-bound hazard is designed out rather than guarded.** ADR-0049 needed `getSolutions` to
  discard any bound an incoming problem carried, because `withObjectiveBound` was public API. With no
  such API, a `LinearBoundConstraint` in a caller's problem is the caller's own constraint and is
  honoured — which is correct, and one fewer sharp edge of the kind
  [ADR-0048](0048-a-bound-conditional-nogood-lives-for-one-search.md) exists for.
- **The cut is part of the problem's `equals`/`hashCode` again**, via the constraint graph. That is
  more defensible than ADR-0049's exclusion: a cut changes the solution set, so two problems differing
  in their cut are different problems. Nogoods remain excluded, correctly, because they do not.
- `FixpointConsistency.filterCache`'s `source` reference still changes mid-solve, now because
  branch-and-bound rebuilds the constraint set rather than because a layer moved. Its Javadoc says so,
  and the reason the rebuilt index stays affordable is unchanged: the rate is bounded by learned
  nogoods and improving solutions, never by nodes.
- What this obligates: the declaration policy (step 4) remains load-bearing and counter-intuitive, so
  any change to *when* the scope is declared owes the four-arm run above. `GolombRuler-09-a4`,
  `Fastfood-ff10` and `TravellingSalesman-20-30-00` are the three instances to look at first.
