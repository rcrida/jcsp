# 0032. Reset dom/wdeg weights after a run of restarts that make no progress

**Status**: Accepted

## Context

`Bibd-sum-06-050-25-03-10` and `Bibd-sc-06-050-25-03-10` were sharply bimodal. Across twelve seeds,
six solved in 0.6-2.1s (1,539-7,924 nodes) and six exceeded 60s (>250,000 nodes). There was nothing
in between: the gap from 7,924 to 250,000 nodes was empty, and the two instances — the same problem
in two encodings — were fast and slow on exactly the same seeds.

This is the heavy-tailed behaviour backtracking search is known for on highly symmetric problems
(Gomes, Selman, Crato & Kautz 2000), whose standard remedy is randomized restarts. The slow runs
*did* restart — 157 times — and never escaped, so the restarts were not behaving as independent
draws.

Two candidate causes were tested by building each and measuring:

- **{@code PhaseMemory} ratchets a doomed path.** Its `bestDepth` only ever grows and `bestPath`
  is never cleared, so a restart reaching depth D in a dead region can pin the value ordering
  there. **Refuted**: disabling phase memory entirely left five of six slow seeds slow and flipped
  a fast seed to slow. It reshuffles which seeds are lucky rather than removing the mode.
- **The dom/wdeg weights correlate the restarts.** With nogood learning off by default (ADR-0030)
  and phase memory ruled out, accumulated constraint weights are the only state surviving a
  restart. **Confirmed**: resetting them on every restart took the twelve seeds from 6/12 to 12/12.

Instrumenting a slow run showed the pathology directly. Across ~231 restarts the deepest assignment
reached went 0, 42, 165, then **193 for 12 consecutive restarts, 202 for 158, and 215 for 58** — on
a 1050-variable problem. The search was not inching forward; it was re-running one ordering.

## Decision

`DomWdegLubySearch#getSolution` tracks the deepest assignment `PhaseMemory` has recorded. A restart
that fails to raise it counts as stagnant, and after `STAGNANT_RESTART_LIMIT` (32) consecutive
stagnant restarts the selector's weights are returned to 1 via
`DomWdegVariableSelector#resetWeights`, which also clears the last-conflict variable. Any restart
that reaches a new deepest assignment clears the counter.

Weight accumulation across restarts is otherwise unchanged, so an instance making progress — or
solving inside 32 restarts — behaves exactly as before, bit for bit.

**The limit of 32 is derived, not tuned.** The two corpus instances closest to being affected,
`driverlogw-09` and `qwh-o30-h374-01`, use 17 and 30 restarts *in total*, so 32 consecutive stagnant
restarts is unreachable for them by construction. A stuck `Bibd` run plateaus for 158 consecutive
restarts, so it still resets several times. Both were verified at the corpus seed: `driverlogw-09`
8,151 nodes / 17 restarts and `qwh-o30-h374-01` 36,741 nodes / 30 restarts, identical to baseline.

Ordering is all this affects, so soundness and completeness are untouched. `getSolutions` never
restarts and is unaffected.

## Rejected alternatives

- **Reset weights on every restart.** Fixes Bibd (12/12) but is pure amnesia: previously-fast seeds
  degraded 10-30x (seed 4, 1,716 -> 51,478 nodes), and the corpus went to 71 rather than 72 because
  `driverlogw-09` regressed. Checked in isolation over four seeds it was 3/4 against the baseline's
  own 3/4 — no more reliable there, just 2-3x more nodes per solve.
- **Halve weights on every restart** (VSIDS-style decay). Worse than either extreme: 10/12 on Bibd,
  still bimodal, and it flipped two previously-fast seeds (2 and 11) to timeouts.
- **A stagnation limit of 8.** Fires far too early — the first Luby budgets are tiny (100, 100, 200
  with the default unit), so an instance that simply has not been given enough budget yet looks
  stagnant. It cost both `driverlogw-09` (8,151 -> 19,442 nodes) and `qwh-o30-h374-01` (36,741 ->
  114,513), leaving the corpus at 70. This is why the limit is set from observed restart counts
  rather than picked for how quickly it rescues Bibd.
- **Disabling or weakening phase memory**, per the refutation above. ADR-0026's deepest-path-only
  rule stands unchanged.

## Consequences

Corpus at a 30s budget, one seed per instance:

| arm | solved | changes vs baseline |
|---|---:|---|
| baseline | 70 | — |
| reset every restart | 71 | +2 Bibd, -1 `driverlogw-09` |
| adaptive, limit 8 | 70 | +2 Bibd, -2 casualties |
| **adaptive, limit 32** | **72** | **+2 Bibd, nothing lost** |

0 failures and 0 `SolutionChecker` mismatches throughout. Over twelve seeds `Bibd-sum-06` goes from
6/12 to 12/12, with the previously-fast seeds keeping their exact node counts (1,539 / 1,596 /
1,716 / 4,001 / 6,318 / 7,924) because the reset never fires on them.

The cost is borne by the runs that were already failing: a rescued seed takes more nodes than a
lucky one (seed 1: 187,019 nodes against a lucky seed's 1,716), since the reset only fires after 32
restarts have been spent. Trading a timeout for a slow solve is the point.

This obligates any future change to restart-surviving state to ask whether it correlates restarts.
Weights were the last such state only because learning is off by default; if nogood learning is ever
re-enabled by default, the same question applies to the nogood store, and the depth signal this
decision relies on would need re-checking against it.
