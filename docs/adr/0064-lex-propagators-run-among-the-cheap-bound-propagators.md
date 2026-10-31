# 0064. Lex propagators run among the cheap bound propagators

**Status**: Accepted

## Context

`FixpointPropagation.PROPAGATORS` runs its entries in order each round, and repeats rounds until none changes
anything. Every propagator is prune-only, so the order cannot change the fixpoint, only how much work a round
spends reaching it. The catalogue has two deliberate placements: cheap unary and binary bound propagators first,
and AC3 and the nogood store last, so the expensive ones work against the narrowest domains available.

`LexConstraint` and `LexChainConstraint` were in the middle, after the bin-packing, cardinality and sum
propagators. They broke no rule of the ordering, but nobody had measured them. While looking for the cost of
the opt-in chain (ADR-0062), a counter showed the chain was called far less often than the pairs and cost the
same per variable touched; the remaining lever was when it ran.

## Measurements

Order only; `LexConstraint` at its old slot (about 45 of 55), first, or at index 6, after the six cheap
unary and binary propagators. Fixed seed, interleaved, 30s per run.

- **BinPacking-mdd/tab**: node counts identical at every position (153,220 / 100,731 / 47,812 on seeds 3, 4, 6),
  so the search is the same and only the time moves. Proofs 28-37% faster (mdd 27.7s to 19.4s, 20.7s to
  15.2s, 8.5s to 5.5s; tab 25.3s to 17.4s, 19.1s to 13.3s, 7.9s to 4.8s). The unproved seed 5 ran 15-30% more
  nodes per second.
- **BinPacking-sum** (timeout-bound, paths diverge): 68-88% more nodes per second on four seeds; best cost after
  30s equal on three seeds and better on one.
- **Bibd**: identical to the node and the tenth of a second.
- **First against index 6**: within a few points of each other everywhere.

The full 60s corpus sweep, default against early against default again (to measure drift), fixed seed:

| | default | early | default (repeat) |
|---|---|---|---|
| solved | 84 of 85 | 84 of 85 | 84 of 85 |
| optima proved | 25 | 25 | 25 |
| total solve time | 574.4s | 578.7s | 580.0s |

No result or objective differs. Eight corpus instances contain a lex constraint; BinPacking-mdd and -tab were
21-23% faster (5.3s against 6.5s and 6.9s; 4.7s against 6.2s and 6.1s) at identical node counts, BinPacking-sum
ran about 57% more nodes per second to the same outcome, and the other five were unchanged. The two instances
that appeared slower (`MagicSquare-6-mdd`, `GraphColoring-3-fullins-4`) have no lex constraint and differ
between the two default sweeps by as much, so they are drift.

The same effect shows for the chain: its throughput on BinPacking-sum rose 24-45% when moved to the same slots,
so part of what first looked like the chain's cost was where it ran.

## Decision

Both lex entries move to just after `BinaryNotEqualsConstraint`, with the other cheap bound propagators.

## Tried and not adopted

These were measured on the way and changed nothing worth keeping:

- A cheap pre-check that a chain's rows are already strictly ordered: fires on 0.6-7.7% of calls.
- A stateless test that the rows' bounds are already ordered (which is exactly when a call changes nothing):
  sound, but fires on 7-28% of calls and the failed scans cost about a point of run time.
- A lazy chain that reads domains and builds vectors only as far as needed: reads 93-95% of the domains anyway,
  because deciding whether a row can take the previous row's bound needs every position, and runs 10-25% slower.
- Running the chain last (as AC3 and the nogood store are): 15-26% fewer nodes per second on Bibd, neutral on
  BinPacking.

## Consequences

- BinPacking proofs, the gap that started this investigation, are 21-37% faster with the same search.
- The order is not a correctness property and can be moved again; the evidence for it is the measurement above,
  not the position itself.
- Further reordering of the catalogue may be worth measuring the same way. This was found by moving one entry
  and holding the node counts fixed, which is what separates ordering from search behaviour.
