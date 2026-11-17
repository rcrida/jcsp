# 0066. Branch on a small cycle cutset first

**Status**: Accepted

## Context

Cutset conditioning, as `CutsetConditioningSolver` implements it, enumerates the assignments of a
cycle cutset and solves the remaining tree for each one, with no propagation or learning carried
between them. That is a blind enumeration, so it is capped at 10⁶ cutset assignments, and on real
instances it almost never applies. Across all 1,837 XCSP3 series instances available locally, it
accepted a decomposition only for Rlfap components of 4–14 variables, which dom/wdeg solves at once
anyway.

The guarantee cutset conditioning buys is already available inside the main search. MAC
re-establishes arc consistency at every node, and on a tree-structured binary CSP it never
backtracks. So once the assigned variables form a cycle cutset, the rest of the branch is solved
without failure, and DomWdegLubySearch keeps propagation, nogoods, restarts and weights while it
gets there. Cutset conditioning reduces to a variable order: cutset variables first.

## Decision

`AdaptiveVariableSelector.Factory.INSTANCE` is `CutsetFirstSelector`, in both the satisfaction and
optimization chains. It computes a greedy cycle cutset (`FeedbackVertexSet`) of the problem's
constraint graph once per selector. When that cutset holds at most 10% of the graph's variables,
every pick comes from the cutset, ranked by dom/wdeg, until all of it is assigned; after that, and
whenever the cutset is larger, the choice is dom/wdeg's. One `DomWdegVariableSelector` underlies
both phases and receives every event, so weights and last-conflict carry across.
`AdaptiveVariableSelector.Factory.DOM_WDEG` is dom/wdeg alone.

`DomWdegLubySearch` builds its selector per connected component, so the gate is applied per
component.

## Rejected alternatives

- **A better cutset inside `CutsetConditioningSolver`.** Replacing its per-seed BFS with greedy FVS
  finds smaller cutsets (Rlfap-graph-13-w1's 884-variable component: 256 → 173 variables), but they
  remain 10³⁰–10³⁷⁶ assignments. Across the 1,837 series instances, FVS changed the
  accept/decline decision on 2 components of 10 variables each.
- **Cutset-first without a gate.** On QRandom reg2ext it was 2.2× faster (21 instances, 3 seeds,
  geometric-mean ratio 0.46, faster in 58 of 63 pairs). On the 85-instance competition corpus it
  regressed: Taillard-os-04-04-0's optimum went from proved in 1.6 s to unproved at 60 s,
  Fastfood-ff10 from 11.8 s to 28.4 s, and Mario-easy-4, GolombRuler-09-a4, BinPacking-mdd/-tab and
  the Vrp instances were all worse. Their cutsets are 41–99% of the variables, mostly auxiliaries,
  so restricting dom/wdeg to them mainly overrides it.
- **A 20% or 40% gate.** On the 35 series instances with a cutset of 10–20%, cutset-first measured
  neutral (3 seeds), so 20% gains nothing over 10%. The regressions begin at 41% (Taillard-os) and
  43% (Mario-easy-4), so 40% would leave no margin, and 217 instances fall between 20% and 40%,
  most of them Fischer, unmeasured.
- **Preferring declared over auxiliary variables**, tried and reverted earlier, failed for the
  reason the ungated version does: forcing wide variables first takes away the cheap,
  propagation-triggering decisions dom/wdeg prefers.

## Consequences

- Variable order changes only where a small cutset exists. In the bundled competition corpus that
  is QuadraticAssignment-bur26a alone (25 of 351 variables, 7.1%): 6 seeds at 60 s averaged 1.9%
  worse, inside its 8% seed-to-seed spread. 78 of the other 84 instances explore exactly the same
  nodes; the remaining 6 run to the time limit on both sides with the same results.
- reg2ext, whose cutset is exactly its 10 decision variables out of 130, is 2.5× faster with the
  gated default (180.1 s → 65.8 s over 21 instances, faster on 20).
- Building the selector now computes the cutset: `O((V + E) log V)` over the constraint graph,
  where every scope counts as a clique. A scope wider than the allowed cutset plus two, or a graph
  over `FeedbackVertexSet.MAX_ADJACENCY` entries, is declined before the graph is built.
- `CutsetConditioningSolver` is unchanged and still runs first in the component chain; whether it
  should stay is a separate question.
