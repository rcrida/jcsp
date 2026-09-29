# 0039. Discard the phase memory when restarts stagnate

**Status**: Accepted

## Context

[ADR-0026](0026-solution-guided-phase-saving.md) gave `DomWdegLubySearch` a `PhaseMemory` shared
across every Luby restart: the deepest assignment ever reached is remembered, and each restart
replays those values first, so it re-enters the deepest path instead of rediscovering it. That is
solution-guided search, and it was measured to help.

[ADR-0032](0032-adaptive-weight-reset-on-stagnant-restarts.md) later found that state surviving a
restart can also *correlate* the restarts into variations on one doomed ordering, and discarded the
selector's weights after 32 restarts without progress — describing weights as "the only such state".
They were not. The phase memory survives every restart too, and is never discarded.

`StripPacking-C1P1` is where that costs everything. The instance is a 20x20 strip packed exactly by
16 rotatable rectangles, and jcsp explored ~900,000 nodes over ~1,500 restarts without ever finding
a packing. Fixing the rotations and leaving all 32 origins free shows the placement search was never
the problem:

| rotations | nodes | solved |
|---|---|---|
| free | 887,404 | no |
| pinned to Choco's solution | 9,488 | yes, 0.43s |
| pinned to all-zeros | **80** | yes, 0.06s |

An all-zeros rotation set is feasible — verified independently of the solver: 16 rectangles, 400 of
400 units, no overlaps, all inside the strip. A solution sat 80 nodes deep and the search spent
900,000 nodes not reaching it.

The cause is the memory, isolated by holding everything else equal and varying only whether each
attempt gets its own:

| per-attempt state | attempts in 60s | solved |
|---|---|---|
| fresh phase memory, fresh weights | 1,458 | **yes, 37s** |
| **shared** phase memory, fresh weights | 1,983 | no |

The trap is that the remembered path is the *deepest* one, not a *completable* one. A rotation
prefix that cannot be finished still looks like the best progress ever made, so it is recorded, and
`prioritise` then steers every later restart straight back into it. Restarting more does not help;
each restart is the same descent.

## Decision

`PhaseMemory#reset` clears the recorded path and its depth, and `DomWdegLubySearch#getSolution`
calls it on ADR-0032's existing stagnation trigger, beside `onStagnation`. The loop's own
`deepestSeen` is cleared with it, so the next restart sets a fresh baseline rather than counting as
stagnant immediately.

The trigger is reused rather than replaced: 32 consecutive restarts that reach no new depth is
already the signal that accumulated state is steering rather than helping, and it is exactly when
"deepest" has stopped meaning "promising".

## Consequences

`StripPacking-C1P1` solves on all three seeds — 186k, 188k and 339k nodes — against a 60-second
timeout before.

Corpus **75 -> 77, 0 failed, 0 `SolutionChecker` mismatches, nothing lost**. The second gain was
unlooked-for: `MarketSplit-01` also solves now (47.7s, 290,546 nodes), having been classified as a
pure *throughput* problem on the grounds that jcsp explored fewer nodes than Choco needed while
running 39x slower per node. That classification measured the right numbers and drew the wrong
conclusion — the search was not merely slow, it was also stuck replaying one descent. Two of the six
instances Choco solved and jcsp did not are now closed; the remaining four are
`BinPacking-tab-n1c1w4a`, `GolombRuler-09-a4`, `Taillard-os-04-04-0` and `Vrp-A-n32-k5`.

ADR-0026's mechanism is unchanged for every search that is making progress; this only fires where
phase saving has demonstrably stopped paying. The Bibd instances that ADR-0026 exists for are
untouched by construction, the same argument ADR-0032 made for the weight reset: they finish in 17
and 30 restarts in total, so a 32-restart stagnation trigger is unreachable for them.

ADR-0032's claim that weights are "the only such state" correlating restarts is superseded. So is
[ADR-0037](0037-energetic-reasoning-for-cumulative-constraints.md)'s conclusion that search order
was blocking this instance, which is corrected in place.

## Rejected alternatives

**Recording only completable paths.** The honest fix for "deepest is not completable", and
impossible as stated: nothing is known to be completable until it is completed, which is the search
itself.

**Discarding the memory every restart.** That is plain restart-with-no-memory, which ADR-0026
already measured as worse where phase saving works, and it would lose exactly the instances that
motivated it.

**Reordering the variables instead.** The first hypothesis, and measured false: five orderings, the
dom/wdeg baseline among them, all fail on this instance, and the two value orderers give identical
node counts. Recorded in ADR-0037's correction rather than here, since that is where the wrong
conclusion was published.
