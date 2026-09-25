# 0033. Replace the search-space snapshot with a root figure and a progress estimate

**Status**: Accepted

## Context

`Statistics#currentSearchSpace` was introduced to answer "how much smaller had the search space
become when an incomplete solve gave up". It is written from seven cancellation-detection sites, each
passing `getSearchSpace()` of whatever `ConstraintSatisfactionProblem` was in scope there — which,
inside a terminal solver's recursion, is the single node the search happened to occupy at that
instant.

That makes it a sample of one node, not a measure of remaining work, and the difference is not
academic. Three runs of one build on `Steiner3-08.xml.lzma` reported **216**, **746,496** and
**10,077,696** — four orders of magnitude apart, decided purely by where in the tree the clock
stopped. It also invites a specific misreading: an instance reporting `nodesExplored=1,606,641`
against a "remaining space" of 46,656 looks like it should obviously have finished. It had in fact
explored about a millionth of a percent of the tree.

## Decision

Two new statistics, and the old one deprecated.

**`rootSearchSpace`** — the whole problem's search space once the chain's one-time preprocessing
fixpoint has converged, recorded by `PropagationFixpointSolver` before any search node is explored.
Both terminal solvers also record their own root as a fallback for a directly-constructed solver
that never ran the chain; `compareAndSet(null, …)` keeps preprocessing's value when both fire. It is
stable across runs of one instance — on `Steiner3-08` all three runs above report
`1,023,490,369,077,469,249,536` — and answers "how much did propagation shrink this problem".

**`remainingSearchSpace`** — `rootSearchSpace` scaled by the unexplored fraction of the depth-first
descent in progress when the solve stopped, via a shared `SearchProgress` accumulator. A node whose
subtree is a fraction `w` of the tree and which branches over `d` candidates gives each child weight
`w / d`; finishing with a child — exhausted, refuted, or pruned, which are indistinguishable and all
mean "will not be visited again" — adds its weight. This is MiniSat's `progressEstimate` with a
per-node branching factor, since a CSP variable's live domain size varies from node to node.

`DomWdegLubySearch` accumulates directly in its recursion and resets per restart.
`BranchAndBoundSolver` searches through a lazy `Stream`, so it attaches the same accounting to each
child sub-stream's `onClose`: `flatMap` consumes a mapped stream fully and then closes it
(try-with-resources in `ReferencePipeline`), so that fires exactly when a child subtree is finished
with. Candidates rejected before `flatMap` — by the limit check or the consistency check — are
completed in the filter instead.

**`currentSearchSpace` is deprecated** (`@Deprecated(since = "3.1.0", forRemoval = true)`) rather
than deleted, because 3.0.0 is published on Maven Central and both `getCurrentSearchSpace` and
`updateCurrentSearchSpace` are public. The seven call sites stay and the `c search-space-after:`
line is still printed; removal is scheduled for 4.0.0.

## Rejected alternatives

- **Deleting `currentSearchSpace` now.** Cleanest end state — two honest metrics and no dead
  surface — but a breaking change against a published release, forcing 4.0.0 for what is otherwise
  a purely additive change. Deprecate-then-remove costs one release cycle and nothing else.
- **Keeping it and only fixing its documentation.** Leaves three `BigInteger` statistics with subtly
  different meanings and keeps printing the number that caused the confusion.
- **Recording progress only at the eagerly-known points in `BranchAndBoundSolver`** (consistency and
  inference failures), avoiding the `onClose` hook. Rejected as unsound: fully-explored subtrees
  would never be counted, so "remaining" would be biased high — misleading in exactly the way this
  ADR exists to fix. The first draft of this work wrongly concluded the lazy-stream shape admitted
  no honest hook at all and scoped the estimate to the satisfaction chain; `onClose` is that hook.
- **Restructuring `BranchAndBoundSolver` to eager recursion** so the accumulator threads through a
  plain loop. That is the ADR-0028 restructure minus restart-on-solution — a rewrite of the whole
  optimization terminal, unnecessary once `onClose` was recognised as sufficient.
- **A cumulative estimate across restarts.** Would need nogood recording from restarts to dedupe
  overlapping regions; ADR-0030 measured that mechanism as not paying its way. The per-restart
  scoping is documented instead.

## Consequences

The unwinding hazard that `currentSearchSpace` already handles applies here too: on cancellation a
lazy stream unwinds, closing sub-streams for children that were never finished, which would inflate
the explored fraction. Both new fields use the same `compareAndSet(null, …)` first-write-wins rule,
and the estimate is recorded *at* detection, so the accurate value is captured before any unwinding
can overwrite it.

Measured on `Steiner3-08` (satisfaction, 15s budget) the three seeds report an identical
`at-root` and a `remaining` that varies only in the sixth significant figure, against an
`after` that swings from 216 to 10,077,696. On `ChessboardColoration-07-07` (optimization) the
estimate decreases monotonically with budget — `3.234416…e84` at 2s against `3.234404…e84` at 4s —
confirming the `onClose` accounting tracks real progress rather than stream lifecycle noise.

`SearchProgress` clamps into `[0, rootSpace]`: it sums many small doubles and can drift past 1.0 on
a long descent, and a negative "remaining" would be worse than a slightly imprecise one.

`Xcsp3CompetitionRunner`'s table drops the deprecated column and carries three instead: `Space
Before` (declared), `Space At Root`, and `Space Left` **as a percentage of the root**. The
percentage is not cosmetic — both figures routinely run past twenty digits and the table's
scientific-notation compaction renders them identically (`1.023e21` against `1.023e21`), hiding
exactly the difference the column exists to show. Six decimal places, because a hard instance sits
very close to 100%: `Steiner3-08` leaves 99.999995% after 12s, which fewer places would round to a
flat 100%, while `ChessboardColoration-07-07` leaves 95.490647% after 10s.

This obligates any future terminal solver to record `rootSearchSpace` on entry if it wants
`remainingSearchSpace` to be populated, since the estimate is meaningless without a root to scale.
