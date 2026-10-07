# 0015. Seeded per-restart tie-breaking, random by default

**Status**: Accepted

## Context

`DomWdegLubySearch.getSolution()`'s ~2x `nodesExplored` variance across separate JVM launches on
identical code and input (confirmed empirically via the pigeonhole benchmark scenario) turned out to
be an accidental side effect, not a designed mechanism: `AC3.buildArcIndex`
(`consistency/arc/AC3.java`) collects arcs via `Collectors.toUnmodifiableSet()`, which mixes in
`java.util.ImmutableCollections.SALT32L` — a value seeded from `System.nanoTime()` once per JVM
process. That salted arc order is memoized once per `ConstraintSatisfactionProblem` structure and
reused for the *entire* search, including every Luby restart, so one arbitrary draw is locked in per
process and cascades — via which arc's domain wipeout gets blamed, which nogood is learned, and
which dom/wdeg weight updates follow — into different search behaviour launch-to-launch.

This is a degenerate version of a real, established technique: Gomes/Selman's randomized-restarts
result, which is exactly what Luby restarts are designed to exploit (heavy-tailed backtracking
runtime → controlled per-restart randomization reduces variance and tail risk). The accidental
version fails to deliver that benefit in two ways: it's frozen for the whole process (the `ArcIndex`
is cached once and reused unchanged across every restart, so restarts never actually re-diversify
along this axis), and it's neither seedable nor reproducible.

The goal: give `DomWdegVariableSelector`'s tie-breaking (used by `DomWdegLubySearch.select`) a
genuine, controlled, reproducible-when-asked source of per-restart randomization, and decide whether
it should be on by default.

## Decision

- **`RestartRandomization`** (new `@FunctionalInterface`, `solver` package): `randomFor(int
  restartIndex) -> @Nullable Random`. `NONE` always returns `null` (today's pre-existing behaviour:
  deterministic first-tied-candidate wins, zero `Random` overhead). `seeded(long baseSeed)` derives a
  distinct `Random` per restart from one internal driver `Random`, advanced via `nextLong()` per call
  rather than `baseSeed + restartIndex` (avoids adjacent-seed correlation); the driver's `nextLong()`
  is safe to call concurrently, so one instance can be shared across `IndependentSubproblemSolver`'s
  concurrently-solved subproblems.
- **`DomWdegVariableSelector`** gains mutable `tieBreakRandom` state and `reseedTieBreak(@Nullable
  Random)` (renamed `onRestart` in 3.1.0, when it became a hook on `AdaptiveVariableSelector` —
  see [ADR-0038](0038-injectable-variable-selector-factory.md)). `select()` now collects *every* variable tied at the minimum dom/wdeg ratio (previously a
  plain `Stream.min(Comparator.comparingDouble(...))`, which silently kept whichever tied candidate
  was encountered first) and, when a `Random` is set, picks uniformly among the tied set; `null`
  reproduces the exact old first-encountered behaviour.
- **`DomWdegLubySearch`** gains a `restartRandomization` field (`DomWdegLubySearchBuilder` defaults
  it to `NONE`) and calls `selector.reseedTieBreak(restartRandomization.randomFor(k))` at the top of
  each restart in `getSolution()`'s loop. `getSolutions()` (the non-restart complete-stream path) is
  untouched — there is no restart to diversify there.
- **`SolverConfig.restartRandomization` defaults to `RestartRandomization.seeded(freshBaseSeed)`**
  (a `ThreadLocalRandom`-drawn seed per `SolverConfig` construction), **not** `NONE` — unlike every
  other `SolverConfig` field (`cancellation`, `listener`), which defaults to an inert no-op. This was
  a deliberate reversal mid-implementation (see Rejected alternatives): the original plan defaulted
  to `NONE` to avoid silently changing behaviour for existing callers, but that framing missed that
  "deterministic tie-break" was never actually "deterministic search" — `getSolution()`'s output was
  already unreproducible launch-to-launch via the AC3-salt effect above. Defaulting to random makes
  the *existing* randomness axis a controlled, useful one instead of leaving a second, purely
  accidental axis as the only source of diversification.

## Rejected alternatives

- **Randomizing AC3's arc-processing order directly** (fixing the actual salted-collection root
  cause) instead of adding a new mechanism. Rejected as the primary fix: `ArcIndex`/`ConstraintGraph`
  are shared infrastructure used by every chain (`TreeSolver`, `BranchAndBoundSolver`,
  `PropagationFixpointSolver`), so changing their ordering semantics is separate, higher-blast-radius
  work with a different goal (pure reproducibility, not restart diversification) — left as a
  possible future task, not attempted here.
- **`SolverConfig.restartRandomization` defaulting to `NONE`** (the original plan, presented to and
  approved by the user before implementation started). Reversed after a direct challenge mid-session:
  since `getSolution()`'s behaviour was never actually reproducible launch-to-launch regardless of
  this field, keeping tie-breaking deterministic by default preserved determinism along only one axis
  while the AC3-salt axis remained fully accidental — see benchmark evidence below for a concrete
  case (quasigroup completion order 20) where that accidental axis produced *zero* variance at all,
  while the new seeded mechanism did.
- **A plain `Random` field with `null` meaning "off"**, threaded directly instead of via a
  `RestartRandomization` indirection. Rejected in favour of the sentinel-object pattern already
  established by `Cancellation.NEVER`/`SolverListener.NONE` in this same package, and because
  `seeded(baseSeed)` needs to derive a *fresh* `Random` per restart call, which a single shared
  `Random` field can't express without the caller re-deriving it externally anyway.

## Consequences

- `getSolution()`'s output (both `nodesExplored` and, for problems with multiple solutions, *which*
  solution comes back) is not reproducible across separate calls with an unconfigured `SolverConfig`
  — it wasn't fully reproducible before either (AC3 salt), but this makes the non-reproducibility
  more pronounced and now spans every solve, not just ones that happen to hit a tie. A caller that
  needs reproducible search behaviour must pass `RestartRandomization.seeded(fixedSeed)` explicitly;
  `RestartRandomization.NONE` restores the pre-2026-08-14 deterministic-tie-break baseline exactly
  (still not launch-to-launch reproducible, since AC3 salt is untouched).
- `DomWdegLubySearch.builder()`'s own direct-construction default (used throughout its own test
  suite and by `NogoodPropagationBenchmark`) stays `NONE`, deliberately *not* matching
  `SolverConfig`'s new default — direct construction bypassing `SolverConfig` is the lower-level,
  test/benchmark-facing API, where deterministic node counts across repeated runs are more valuable
  than realistic default behaviour.
- Verified via `NogoodPropagationBenchmark#compareRestartRandomization` (15 samples each, one
  scenario UNSAT/Golomb ruler order 7, one SAT/quasigroup completion order 20): launch-to-launch
  spread (`RestartRandomization.NONE`, 15 separate `java` process launches) vs. seeded spread
  (`RestartRandomization.seeded`, 15 different seeds within one process). Golomb ruler: comparable
  spread magnitude between the two (launch stddev≈231, seeded stddev≈221) — the new mechanism is at
  least as diversifying, and is additionally seedable/reproducible where the old one wasn't.
  Quasigroup completion: launch-to-launch spread was **exactly zero** (all 15 launches: 405 nodes,
  stddev=0.0) — the AC3-salt effect happened not to matter for this instance's arc topology at all —
  while the seeded spread showed real variance (stddev≈14.7, range 400-450). This is the concrete
  case motivating the default-to-random reversal above: an accidental mechanism that silently does
  nothing for some instances is strictly worse than a controlled one that reliably diversifies.

## Rejected (2026-10-05): three attempts to depend less on the random tie-break

Every obvious way to lean less on `tieBreakRandom` was built and measured, and all of them lost
instances against the uniform draw per tie that this ADR introduced. Taken together they say
something worth knowing: **on this corpus, blind per-node randomness in tie-breaking beats both
information and reproducibility.** That is the opposite of what one would guess, which is why it is
recorded at length rather than as a line.

| | corpus solved | lost | gained |
|---|---|---|---|
| per-tie uniform draw (kept) | **84** | — | — |
| per-variable key, below | 82 | `qwh-o30-h374-01`, `MarketSplit-01` | none |
| conflict-activity key, most-active first | 81 | those two plus `BinPacking-sum-n1c1w4a` | none |
| conflict-activity key, least-active first | not swept | see below | — |

**The third attempt is what makes the conclusion precise: the direction of the informative key does
not matter, narrowing does.** Preferring the *least*-active candidate is the natural inversion — it
complements `lastConflictVariable`'s fail-first override instead of doubling down on it, and it is
self-balancing, since a variable grows less preferred as it accumulates activity, so which tied
candidate wins keeps rotating. It recovered one of the instances the most-active version lost and
lost another outright. Across the three canary instances at two seeds each, the kept uniform draw
solves 6 of 6 and the inversion 3 of 6:

| instance | per-tie draw | most-active | least-active |
|---|---|---|---|
| `qwh-o30-h374-01` | 2 of 2 | 1 of 3 | **0 of 2** |
| `MarketSplit-01` | 2 of 2 | 3 of 3 | 1 of 2 |
| `BinPacking-sum-n1c1w4a` | 2 of 2 | lost in the sweep | 2 of 2 |

Two opposite orderings of the same signal both losing instances rules out "wrong direction" as the
explanation and leaves the narrowing itself. Whatever a secondary key prefers, it decides what the
draw would otherwise have varied, and that variation is the thing earning its keep.

A caveat on that precision: both lost instances sit near the time limit (45s and 13s of 60s), and
`AC3`'s arc order is salted per JVM independently of this mechanism's seed, so an instance at the
boundary can flip between runs — `MarketSplit-01` solved at 3 of 3 seeds in isolation and still came
back UNKNOWN in the sweep. The counts are therefore approximate; the direction, two independent
attempts losing instances and gaining none, is not.

### The tie structure, measured first

On `Taillard-js-015-15-0`: **73% of selections have no tie at all; 27% do, with a mean tie set of 6.3
and a maximum of 31; and the very first selection is a 14-way tie out of 241 unassigned variables.**
So there was a real lever, concentrated exactly where it matters most — early decisions are what
decide whether a heavy-tailed instance falls into its tail.

One structural fact constrains any secondary key. Within a tie set `dom/wdeg` is equal, so domain
size and weighted degree are **proportional** there: ordering tied candidates by "smallest domain"
and by "largest weighted degree" produce the same order, and both merely re-read the quantity that
already tied. A useful key has to be independent of the ratio.

### Attempt 2: a conflict-activity key, narrowing the tie set

`onValueRejected` already names the variable a candidate was rejected on, and `lastConflictVariable`
already exploits the most recent one as an override. Generalising that to a per-variable counter gives
an independent, informative ranking (the activity-based/VSIDS idea), used to **narrow** the tie set
before the random draw rather than to replace it — deliberately, given attempt 1's result below.

Corpus **84 → 81**, three instances lost and none gained. The likely reason is the same as attempt
1's, arrived at from the other direction: narrowing the tie set removes the *opportunities* for the
per-node draw, so an effective informative key suppresses the very diversification that turns out to
be load-bearing. An informative key and a blind draw are not complements here; they compete for the
same decisions.

### Attempt 1: a per-variable tie-break key instead of a draw per tie

`DomWdegVariableSelector.select` collects every variable whose `domainSize / wdeg` equals the best
and picks one with `tieBreakRandom.nextInt(tied.size())` — one draw per tie. Measured on
`Taillard-js-015-15-0`: **73% of selections have no tie at all, 27% do with a mean tie set of 6.3 and
a maximum of 31, and the very first selection is a 14-way tie out of 241 unassigned variables.**

Drawing per tie makes the choice depend on *how many ties were broken earlier*, so two searches that
differ by one decision decorrelate completely from that point on. That is not a small effect. Solving
one fixed problem three times from a single `RestartRandomization.seeded` driver took 8,770, then
20,873, then more than 56,000 nodes; it is also what made an identical problem look easy in one
harness and impossible in another, costing a long detour in
[ADR-0044](0044-bounded-probes-for-the-starting-incumbent.md).

So the tie-break was changed to a **per-variable key**: one `long` drawn per restart, mixed with the
variable's identity through SplitMix64's finalizer, with the smallest key winning. A pure function of
(seed, variable), immune to draw position, and it also removed the per-selection `ArrayList`.

It worked as designed and was still rejected. The structural property arrived — three differently
assembled copies of one problem went from wildly different to 52,394 / 52,275 / 51,564 nodes, i.e.
consistent with each other — but the corpus fell from **84 solved to 82**, and a three-seed check on
the two lost instances showed that is not seed luck:

| instance | per-tie draw (kept) | per-variable key (rejected) |
|---|---|---|
| `qwh-o30-h374-01` | 2 of 3 seeds solved | 1 of 3 |
| `MarketSplit-01` | **3 of 3** | 1 of 3 |

The mechanism is worth recording, because it is the opposite of the stated intent of this ADR.
Drawing per tie supplies **intra-restart** diversification — every node's tie is an independent
coin flip, so one long search keeps varying its ordering — whereas this ADR only ever set out to
diversify *between* restarts. A per-variable key keeps the inter-restart diversity and silently
removes the intra-restart kind, and the instances that lost are exactly the ones that run as a
single long search rather than many short restarts. The accidental half of the mechanism turns out
to be load-bearing.

Anyone reaching for this again should know the comparability problem is real and still unsolved; the
fix has to preserve per-node variation, not just per-restart variation.

## Rejected (2026-10-07): `randomFor` as a pure function of `(baseSeed, restartIndex)`

The section above closed by naming this as the remaining candidate: it addresses the cross-search
half of the comparability problem without touching tie-breaking at all. Built, measured, reverted.

`seeded` held one driver `Random` and advanced it per call, ignoring `restartIndex` entirely, so what
a caller got back depended on how many times it had already been called rather than on which restart
was asking. The replacement derived each restart's seed as `mix(baseSeed, restartIndex)` using
SplitMix64's gamma step and finalizer, keeping the anti-correlation the driver existed for while
making the function pure.

Three seeds per arm, full corpus, 60s per instance, machine held awake:

| seed | driver | mixer |
|---|---|---|
| 20260830 | **84** / 804.6s | 83 / 858.7s |
| 7 | **84** / 761.7s | 82 / 844.6s |
| 13 | 81 / 925.7s | 81 / 895.2s |
| mean | **83.0 / 830.7s** | 82.0 / 866.2s |

One instance worse on average and about 4% slower, never better on solved count. `optimum found`
held at 23 in all six runs and none produced a `SolutionChecker` mismatch, so nothing is answered
wrongly — it simply solves slightly less within the budget.

Three seeds is few, and the corpus is bimodal enough that the driver arm alone spans 81 to 84. Seed
13 gives 81 in *both* arms with the same four instances unsolved, which is what a bad seed looks like
rather than a regression. The claim this rejection rests on is therefore the weak one: the mixer is
not better, and is probably marginally worse. It is not established that correlated retries are
harmful in general.

**What the change actually does, read from the code rather than inferred from the numbers.** Every
optimization solve runs several searches — one for the first solution and one per bounded probe, each
built by `BoundedFirstSolution`'s own `search.apply(...)` (ADR-0044). Each is a fresh
`DomWdegLubySearch` whose `getSolution` starts its restart loop at `k = 1`. Under the mixer the
first-solution search and all up to eight probes therefore receive the *same* seeds —
`mix(baseSeed, 1)`, `mix(baseSeed, 2)`, … — and repeat each other's tie-break decisions, where the
driver's statefulness had been handing each search a different stream position. The statefulness was
supplying **cross-search** diversification; the probes were independent draws because of it.

That is the same shape as the section above, one level out: a mechanism's incidental property was
load-bearing, and the argument for removing it described the property it broke as the defect. Twice
on this ADR now.

**Nogood learning is not the explanation, checked rather than assumed.** Correlated retries revisit
subtrees where earlier nogoods would already apply, so the loss of diversity could in principle be
paying for itself through the nogood store. It cannot here, for two independent reasons. Learning is
off by default (ADR-0030) and `Xcsp3ProblemRunner` never enables it — every statistics line in all
six runs reads `nogoodsLearned=0`. And no probe ever sees what an earlier one learned:
`BoundedFirstSolution` builds one probe chain for the whole descent, but
`IndependentSubproblemSolver.getSolution` calls its `innerFactory` per invocation, and that factory
is where `Solver.Factory.satisfactionSearch` builds `NogoodStore.forProblem(sub)` — so every probe
gets a fresh `DomWdegLubySearch` and a fresh store. The mixer's correlation lands exactly where
nogoods cannot benefit, and leaves untouched the place they could — within one search, where the
store is shared across restarts and where restart `k` and `k+1` still get unrelated seeds.

That observation leaves a separate open item: up to nine searches run over near-identical CSPs,
differing only in the objective cut, and each relearns from nothing. Sharing a store across probes is
not obviously sound — probes descend to *tighter* bounds, and a nogood derived under a tighter cut
does not hold under a looser one — so the direction needs arguing before it is worth measuring.

A derivation keyed on `(baseSeed, searchIndex, restartIndex)` would give comparability *and*
cross-search diversity, which is strictly better than either arm here. It needs the solve to thread a
search identity into `RestartRandomization#randomFor`, widening a published functional interface, so
it is a deliberate API decision rather than a follow-on — and it replaces this as the remaining
candidate.

Kept from the attempt, both independently useful: `Xcsp3CompetitionRunner` takes an optional seed
argument, without which a multi-seed sweep of this corpus is not possible at all, and
`Xcsp3ProblemRunnerTest`'s multi-improvement case no longer pins a seed literal whose behaviour
depends on the exact derivation being tested.
