# 0054. The restart schedule trades diversification against refutation, so the default stays Luby and the alternative is opt-in

**Status**: Accepted

## Context

`ssa-2670-130` (a SAT-shaped XCSP3 instance, unsatisfiable, 1,359 variables, 2,366 table constraints)
is proved UNSAT by Choco in 0.8s with 18,686 nodes and 18 restarts. jcsp did not finish it in 30s,
nor in 900s: 682,428 backtracks across 1,489 restarts.

Two causes had to be separated. **Propagation strength**: a brute-force GAC fixpoint removed nothing
from jcsp's fixpoint, at the root or after 200 random decisions, so jcsp is already arc-consistent
on every constraint here and the trees are comparable. **Search cost per node**: that was
about 600 backtracks/s against Choco's roughly 23,000 and is a separate piece of work (the
assignment, domain-map and fixpoint copies). Once nodes were cheaper, what remained was the
schedule. With the restart budget unbounded and large enough, one run proves the instance in about
40,000 backtracks (`DEFAULT_LUBY_UNIT` raised to 10,000: 9.3s on one seed). Under the default, unit
100, the first Luby budget that large arrives only after about a thousand shorter restarts, some
500,000 backtracks of them, so the proof lands after roughly two minutes on a lucky seed and never
on others. Disabling the stagnation reset ([ADR-0032](0032-adaptive-weight-reset-on-stagnant-restarts.md))
does not rescue it: seeds 1 and 2 stay unsolved at 120s, and seed 3 finishes at 113s.

The same short restarts are what keep the satisfiable, heavy-tailed instances solvable. `Bibd` was
bimodal across seeds before restarts, and `MarketSplit-01` needs 186-298 of them.

## Measurements

Each cell is "instances solved within the cap / seeds tried", on an engine doing about 4,000
backtracks/s. The samples are small and these instances are heavy-tailed, so single cells are weak
evidence; the pattern across rows is the finding.

| Schedule | `ssa-2670-130` (UNSAT, 120s) | `Bibd-sc` (SAT, 60s, 6 seeds) | `MarketSplit-01` (SAT, 60s) |
|---|---|---|---|
| Luby, unit 100 (default) | 0/3 | 6/6, slowest 11.7s | 2/2 |
| Luby, unit 1,000 | 2/2 (44-81s) | 5/6 | |
| Luby, unit 10,000 | 2/2 (9s, 59s) | | |
| Geometric 100 × 1.3 | 2/2 (50-63s) | | |
| Geometric 100 × 1.5 | 2/2 (42-59s) | | |
| Geometric 1,000 × 1.3 | 2/2 (23-29s) | **3/6** | |
| Luby for 128 restarts, then × 1.3 | 3/3 (27-32s) | unchanged (finishes inside 128) | 1/2 |
| Luby for 128 restarts, then × 1.5 | 3/3 (20-27s) | unchanged | 1/2 |
| Interleaved by work, Luby + 1,000 × 1.3 | 3/3 (50-65s) | 6/6, slowest 26.1s | **0/2** |
| Interleaved by work, Luby + 100 × 1.5 | 3/3 (29-66s) | 5/6 | 1/2 |

Every schedule that proves `ssa-2670-130` gives up satisfiable seeds somewhere, and the losses are on
the same instances. A schedule that restarts frequently cannot finish a refutation that needs one long
run, and one that lets a run grow cannot diversify; neither is dominated.

## Decision

Keep Luby with `DEFAULT_LUBY_UNIT = 100` as the default, and add `RestartSchedule` to `SolverConfig`
(`restartSchedule`, default `LUBY`). `INTERLEAVED` alternates by **work**, always running whichever
of two tracks has spent fewer failures: the Luby track, and a geometric one (100 × 1.5^j). Each gets
about half the effort, so a satisfiable instance is at most about twice as slow to solve while a
refutation gets a long enough run after a bounded number of restarts. Through `SolverConfig` it proves
`ssa-2670-130` in 31.8s on seed 1, with the identical 132,983 backtracks and 201 restarts the
prototype measured.

Ordering only: both schedules visit the same assignments, so soundness and completeness are untouched.

## Rejected alternatives

- **A larger default Luby unit, or a geometric default.** Proves `ssa-2670-130` but loses `Bibd-sc`
  seeds (5/6, and 3/6 for the 1,000 × 1.3 schedule), the instances restarts were added for.
- **Luby, then geometric after a fixed number of restarts.** The best on `ssa-2670-130` (20-27s) and
  unchanged on anything solved within 128 restarts, but it stops restarting around restart 140,
  which lost `MarketSplit-01` seed 1.
- **Interleaving as the default.** Keeps restarting, but costs `MarketSplit-01` a seed or two and
  `Bibd-sc` its slowest-seed headroom. Not obviously worse overall, and not clearly better either,
  which is not enough to change a default the corpus was tuned against.
- **Switching schedule on stagnation.** An unsatisfiable instance and a hard satisfiable one both
  plateau, so the signal cannot tell them apart.

## Consequences

- The default behaviour and the competition corpus are unchanged.
- A caller with unsatisfiable-heavy workloads opts in with `.restartSchedule(RestartSchedule.INTERLEAVED)`.
- `ssa-2670-130` remains unsolved by the default configuration in 30s.
- Revisit if a restart policy appears that is not a function of the schedule alone, for example one
  that reuses what a cut-off run learned (nogood learning is off by default, [ADR-0030](0030-nogood-learning-is-not-cdcl.md)).
