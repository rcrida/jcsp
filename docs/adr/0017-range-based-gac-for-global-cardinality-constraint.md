# 0017. Range-based GAC for GlobalCardinalityConstraint

**Status**: Accepted

## Context

`GlobalCardinalityConstraint`'s flow-based GAC algorithm ([ADR-0016](0016-flow-based-gac-for-global-cardinality-constraint.md))
modeled each tracked value's occurrence count as an exact quota (`lo == hi`). XCSP3's `cardinality`
construct also has a min/max occurrence-range form (`<occurs> 1..2 1..2 ... </occurs>`), needed by
the bundled `SportsScheduling-08.xml.lzma` competition instance ("each team plays at most two times
in each period", i.e. a `[1,2]` range per period, not an exact count).

The general `[lo, hi]` range form is Régin's own original 1996 formulation — the exact-count case
jcsp already had is the specialization `lo == hi`. Extending the *flow network* (new edge
capacities, updated required-flow total) is a direct generalization of the existing lower-bound-
elimination reduction. Extending the *GAC filtering* step is not: the exact-count case's
`(trackedValue, T)` edges are always forced (`lo == hi`, reduced capacity zero), so they never carry
real residual capacity and can be omitted from the residual graph entirely — `buildResidualGraph`'s
SCC computation only ever needed the bipartite variable/value portion of the network. A real
`[lo, hi]` range breaks that: the edge now has genuine positive residual capacity, which is a real
source of new reachability between otherwise-unconnected tracked values that the bipartite-only
residual graph can't represent.

## Decision

Extended `GlobalCardinalityConstraint` in place (not a new sibling class) since the flow algorithm
generalizes cleanly: `cardinalities: Map<T, Integer>` became `cardinalityRanges: Map<T,
OccurrenceRange>` (a new public nested record, `min`/`max` inclusive), with `of(Set, Map<T,Integer>)`
kept at its existing signature (converts each count to `OccurrenceRange(v, v)` internally) and a new
`ofRange(Set, Map<T, OccurrenceRange>)` factory for the general case.

`computeFlow` adds one new edge per tracked value with `hi_v > lo_v` — `(trackedValue, sinkOriginal)`
with reduced capacity `hi_v - lo_v` — alongside the existing forced-`lo_v` supersink edge; skipped
entirely when `hi_v == lo_v` (the exact case), so the reduction is unchanged for existing callers.
`buildResidualGraph` includes `sinkOriginal` itself as one extra node, with residual edges built
from **the edge's own residual capacities directly** — `hasResidualCapacity`/`hasFlow` on that one
excess edge, and on the pre-existing `(untrackedNode, sinkOriginal)` edge (previously omitted
entirely, since its own residual capacity provably never added information beyond what each
variable's own edge already gave — see ADR-0016's Javadoc, still true, but now other tracked
values *can* reach it transitively through `sinkOriginal`, so it must be represented once
`sinkOriginal` is a real node at all).

No value-node splitting was needed. The `[lo, hi]` bound sits on an *edge* (`value → sinkOriginal`)
in this formulation, not a node, and residual graphs already represent edge capacity bounds natively
via plain forward/backward residual edges on that edge — node splitting is a different technique for
representing a *node's own* capacity bound, which doesn't apply here.

Validated via the same randomized-cross-check pattern ADR-0016 established
(`propagateRange_randomizedCrossCheckAgainstBruteForceGac`, 300 random small instances against
exhaustive brute-force GAC over `[lo, hi]` ranges) plus an ad hoc, larger out-of-tree stress run
(20 seeds × up to 2000 trials each, up to 5 variables and 4 tracked values, ~22.7k checked
combinations) before trusting the derivation — not run as part of the committed suite, but the
scale of independent verification this specific change warranted before landing it.

## Rejected alternatives

- **Decompose into `CountConstraint` pairs** (`count(vars,v) >= lo` and `count(vars,v) <= hi` per
  tracked value) instead of touching the flow algorithm at all. Considered first as the safer,
  lower-risk option — reuses already-tested machinery, no risk to a propagator with its own ADR and
  existing test suite. Rejected in favor of true GAC once the actual scope of the residual-graph
  extension became clear and tractable (see below); the decomposition's weakness (independent
  per-value bounds only, no cross-value consistency — the same class of gap ADR-0016 itself closed
  for the exact-count case) would have reintroduced exactly the blind spot ADR-0016 fixed, just for
  the range case specifically.
- **Value-node splitting** (splitting each tracked-value node into `_in`/`_out` copies with an
  internal residual edge representing "can grow"/"can shrink" capacity) — the standard technique
  recalled from CP literature for *node*-capacitated flow formulations. Initially assumed necessary
  and flagged as a real correctness risk before implementing (asked the user how to proceed given
  the risk). Turned out to be solving the wrong problem: jcsp's GCC formulation puts the `[lo, hi]`
  bound on an *edge* (`value → sinkOriginal`), not a node, so the bound's residual capacity is
  already representable as an ordinary forward/backward residual edge pair on that one edge — no
  splitting required. Re-deriving the exact edge topology from first principles (rather than
  pattern-matching to a remembered but not-quite-applicable technique) both simplified the
  implementation and removed the corresponding risk.

## Consequences

- `GlobalCardinalityConstraint.of(...)`'s public signature and behavior are unchanged; every
  existing test in `GlobalCardinalityConstraintTest` (including the exact-count randomized
  cross-check from ADR-0016) passed unmodified except one cosmetic `toString()`/`getRelation()`
  format change (`RED=2` → `RED=2..2`).
- The one direct-builder test that bypassed `of()`'s structural assert
  (`explainInfeasible_structuralOverCommitment_violatingSetIsEmpty_returnsEmptyReason`) needed
  updating for the renamed field (`cardinalities` → `cardinalityRanges`, `Integer` → `OccurrenceRange`
  values) — a mechanical fixup, not a behavioral one.
- `ConstraintSatisfactionProblem`'s builder gets `globalCardinalityRangeConstraint(Set, Map<T,
  OccurrenceRange>)` as a distinctly-named sibling of `globalCardinalityConstraint` (not an
  overload): `Map<T, Integer>` and `Map<T, OccurrenceRange>` both erase to raw `Map`, so a
  same-named overload would collide (JLS 8.4.2) — the same reason `linearBooleanConstraint` needed
  a distinct name rather than overloading `linearConstraint`.
