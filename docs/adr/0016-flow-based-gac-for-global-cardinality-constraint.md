# 0016. Flow-based GAC for GlobalCardinalityConstraint

**Status**: Accepted

## Context

`GlobalCardinalityConstraint`'s original `propagate`/`explainInfeasible` classified each tracked
value independently (definite/possible/impossible, mirroring `CountConstraint`'s own per-value
logic) and narrowed domains value-by-value, with narrowing from one value feeding into the next.
This is real propagation, but it only ever reasons about one tracked value at a time — it misses
joint infeasibility across several values simultaneously (a Hall-set-style violation: e.g. three
variables each restricted to `{a, b}`, with quotas `a:1, b:1` — infeasible by pigeonhole, but no
single value's own quota is individually violated, so the per-value classification never detects
it). This is the same gap `AllDiffConstraint` would have if it were decomposed into pairwise `≠`
constraints instead of using Régin's matching-based GAC algorithm.

Two designs were scoped to close this gap, both generalizing `AllDiffConstraint`'s existing
Régin (1994) matching + residual-graph-SCC algorithm from 0/1 bipartite matching to a global
cardinality constraint's per-value quotas:

- **Node-exploding matching**: explode each tracked value `v` (quota `n_v`) into `n_v` identical
  capacity-1 "slot" copies, merge every untracked value into one group of `n - Σn_v` slots, and
  run the exact same plain-bipartite-matching + SCC code `AllDiffConstraint` already has, since
  total slots always equal `n` (a perfect-matching problem).
- **True flow with lower bounds** (Régin 1996's actual generalization of his own 1994 paper):
  model each tracked value's `[n_v, n_v]` capacity directly as a flow edge (no exploding), find
  feasibility via the standard supersource/supersink lower-bound-elimination reduction to ordinary
  max-flow, then generalize the residual-graph SCC filtering from matching to flow.

## Decision

Built the flow-with-lower-bounds version (`GlobalCardinalityConstraint.computeFlow`,
`buildResidualGraph`, `tarjanSCC`, plus a private `MaxFlow` — a minimal Edmonds-Karp
implementation with paired-reverse-edge residual tracking). Every untracked value is merged into
one shared sink node (`FlowNetwork.untrackedNode`): this constraint never needs to know *which*
untracked value a variable takes, only that it can reach one, so merging is lossless and keeps the
network's size independent of how many distinct untracked values appear.

`explainInfeasible`/`findViolatingSubset` extracts the violating variable subset via the standard
max-flow-min-cut construction: the variable-nodes still reachable from the supersource once no
more augmenting paths exist. This single construction subsumes both Hall-type failure modes a
fixed-cardinality GCC can have (over-subscription: too many variables chasing too little combined
value capacity; under-subscription: too few variables able to reach a high-quota value) without
needing to distinguish them — the same way `AllDiffConstraint`'s one matching-based computation
covers every Hall violation without a separate case per shape. The resulting subset is attributed
via `AllDiffConstraint.explainInfeasible`'s exact two-tier fallback (`Propagatable#allSingletonReason`,
else `RangeNogoodConstraint#fromCurrentBounds`).

## Rejected alternatives

- **Node-exploding matching**, despite directly reusing `AllDiffConstraint`'s already-reviewed
  matching+SCC code with much less new machinery. Rejected because this project's own bundled
  XCSP3 competition corpus (`src/test/resources/xcsp3/competition/`) already contains a real
  instance (`BinPacking-sum-n1c1w4a.xml.lzma`) with a `cardinality` quota of 70 out of 120 total
  variables — exploding that single value into 70 slot-copies, each wired to every eligible
  variable, is genuine multiplicity blowup (worst-case `O(n³)` via Kuhn's `O(V·E)` matching with
  `V, E` inflated by quota size), not a theoretical concern. True max-flow (Edmonds-Karp,
  strongly polynomial, independent of capacity magnitude) has no such blowup: graph size stays
  `O(n + distinct values)` regardless of how large any single quota is. Given this library is
  being prepared to run against real XCSP3 competition instances under time budgets, robustness to
  large quotas mattered more than the smaller implementation surface.
- **Keeping the original per-value propagator** and accepting the Hall-set blind spot. Rejected:
  the gap is real and cheaply demonstrable (the pigeonhole counterexample above), and the fix
  strictly subsumes the old algorithm's pruning strength.

## Consequences

- `propagate`/`explainInfeasible` are a full rewrite, not an incremental patch — the flow-based
  algorithm strictly subsumes the old per-value classify/narrow pass, so no case the old algorithm
  handled correctly is weakened, but the specific *shape* of citations in `explainInfeasible`
  changed (a Hall-set-based subset, not necessarily the minimal one a per-value algorithm might
  have cited for a given case) — existing tests asserting exact old-shaped nogoods were updated to
  reflect the new (still sound, sometimes less tight) citations.
- A genuinely new soundness case discovered during implementation: a variable's edge to the merged
  *untracked* sink can itself be GAC-unsafe (forced away from every untracked value) even though
  untracked values are individually unconstrained — this happens when every feasible completion
  needs that specific variable to help meet a tracked quota instead. Missing this was a real bug
  caught by the existing `GlobalCardinalityConstraintTest` suite before any new tests were added.
- The violating-subset extraction's defensive `isEmpty()` case (no variable-node reachable from
  the supersource) is real, reachable code, not speculative defensiveness — confirmed via a
  constructed test (`explainInfeasible_structuralOverCommitment_violatingSetIsEmpty_returnsEmptyReason`):
  a structural over-commitment (`Σ n_v > n`) produces a violating "cut" entirely on the
  value/bookkeeping side of the flow network, with no single variable to blame.
- Correctness confidence for this class of intricate graph algorithm rests partly on a randomized
  cross-check test (`propagate_randomizedCrossCheckAgainstBruteForceGac`, 300 random small
  instances checked against exhaustive brute-force GAC) rather than hand-picked cases alone —
  worth reusing this pattern for any future propagator complex enough that hand-verification of
  every edge case isn't practical.
