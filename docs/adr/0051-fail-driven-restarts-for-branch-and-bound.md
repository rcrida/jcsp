# 0051. Fail-driven restarts for branch-and-bound, on a geometric schedule

**Status**: Accepted

## Context

On `QuadraticAssignment-bur26a` branch-and-bound's own search found nothing after the seeder's
solution: 228k nodes in 60s and exactly one improving solution, against Choco's 106 improving
solutions in the same node budget. Choco does not restart on solution; it continues the DFS from each
leaf and restarts on *failures*, so each improvement is a small change to the last and the restarts
move that neighbourhood around the permutation. Switching its restarts off reproduces jcsp's result.

[ADR-0028](0028-restart-on-solution-rejected-for-branch-and-bound.md) rejected restart-*on-solution*, twice.
This is a different mechanism and does not reopen that measurement: a fail-driven schedule had never
existed in jcsp, and on this instance restart-on-solution fires once because there is one solution to
restart on.

The constraint that shaped the design is that `Xcsp3ProblemRunner` infers `OPTIMUM FOUND` from "the
stream drained and `cancellation` never fired". A restart that ended the stream after a truncated run
would print a proof jcsp does not have.

## Decision

`BranchAndBoundSolver.getSolutions` is a lazy sequence of runs. Each run is a complete DFS from the
root under whatever incumbent exists by then, and is abandoned once it has seen `base * growth^(k-1)`
failed candidates (`base = 50`, `growth = 1.3`).

- **A failure is every dead end**, not only a propagation wipeout: both the `isConsistentAmong`
  rejection and the infeasible inference count. `DomWdegLubySearch`'s own Luby budget counts only
  wipeouts and is untouched.
- **The cutoff grows without bound**, so some run outlasts the whole tree and ends un-aborted. Only
  that run's end drains the stream, so a drained stream is still a proof of optimality — the proof
  rests on the final run alone, not on any carried state.
- **Abandoning is a flag, not an exception.** An exception thrown while a lazy stream is consumed
  escapes to the caller's terminal operation. The flag is checked by a `takeWhile` on each frame's
  candidates, so a cutoff reached anywhere unwinds every frame at its next pull.
- **Runs are chained flat** (`Stream.iterate(...).takeWhile(...).flatMap(...)`), not by nesting
  `Stream.concat`, whose depth would grow with the number of restarts.
- **Carried across runs:** the incumbent, the selector's weights, the phase memory, the nogood store
  and the LP gate. Each run reseeds the selector's tie-breaking from `restartRandomization`, which
  `Solver.Factory` supplies from `SolverConfig` on its own search index.
- **Geometric, not Luby.** The work to reach a cutoff `C` is about `C·r/(r−1)` for a geometric factor
  `r`, against `O(C log C)` for Luby; an instance that currently proves optimality needs a run whose
  cutoff exceeds the fails the proof takes, and Luby would tax every such proof harder.
- **No `SolverConfig` knob.** `restartFailBase` and `restartGrowth` are `BranchAndBoundSolver` builder
  fields, because tests need to force restarts; the defaults are the only values the chain uses. The
  solve rejects a base of zero or a growth of `1` or less, since a cutoff that cannot grow can never
  prove anything.

## Measurements

`QuadraticAssignment-bur26a`, 60s, best objective (number of improving solutions); baseline is 2,358,989
(1) on the first seed:

| growth | seed 20260830 | seed 1 | seed 2 |
|---|---|---|---|
| 1.1 | 1,892,980 (34) | 1,900,615 (74) | 2,389,840 (1) |
| 1.3 | 2,229,501 (18) | 1,938,533 (70) | 2,389,840 (1) |
| 2.0 | 2,358,989 (1) | 1,937,930 (59) | 2,389,840 (1) |

Whole corpus, 85 instances, 60s, fixed seed; baseline is 84 solved with 23 `OPTIMUM FOUND`:

| growth | solved | optimum found | lost or newly missed | objectives that improved |
|---|---|---|---|---|
| 1.1 | 84 | 22 | `GolombRuler-09-a4` lost its proof (42.9s → timeout); proofs 2-5x slower on `Fastfood-ff10`, `GolombRuler-09-a3`, `GraphColoring-3-fullins-4`, `LowAutocorrelation-015`, `QueenAttacking-06`, `Taillard-os-04-04-0` | bur26a 1,900,955; `TravellingSalesman-20-30-00` 112 → 104; `Vrp-P-n16-k8` 455 → 450 |
| **1.3** | **84** | **23** | none; proofs 2-3x slower on `Fastfood-ff10`, `GolombRuler-09-a3`, `QueenAttacking-06` | bur26a 2,180,862; `TravellingSalesman-20-30-00` 104; `Vrp-P-n16-k8` 450 |
| 2.0 | 83 | 23 | `MarketSplit-01` timed out | `TravellingSalesman-20-30-00` 104; `Vrp-P-n16-k8` 450; bur26a unchanged |

A smaller factor finds more improvements and costs more proofs, which is the `C·r/(r−1)` arithmetic
showing through: 11C at 1.1, 4.3C at 1.3, 2C at 2.0. 1.3 is the only factor that neither lost a proof
nor lowered the solved count. `TravellingSalesman-20-30-00`'s 104 is its known optimum.

**The acceptance bar was not met.** The plan asked for bur26a to improve by at least 10% with at least
20 improving solutions on all three seeds. At 1.3 it did so on one seed (1,938,533, 70 solutions),
improved on another by 5.5% with 18 solutions, and not at all on seed 2, where the seeder's solution at
~8s is never improved by any of the 50 seconds that follow, under any factor. Why that seed stalls was
not investigated.

## Rejected alternatives

- **Assigning propagation-forced variables in one step first** (a plan prerequisite). Its stated
  premise did not hold, so it was first left out; it was adopted afterwards for what it measured on top
  of this change, not for that premise. See
  [ADR-0052](0052-assign-propagation-forced-variables-in-one-step.md), whose bur26a numbers for the two
  together supersede the "ADR-0051 alone" figures above for the shipped configuration.
- **Luby.** See Decision.
- **A `SolverConfig` knob or a fallback to the unrestarted search.** It leaves two code paths against a
  100% branch-coverage gate with nothing measuring the unused one.

## Consequences

- `Statistics#restarts` on an optimisation solve now includes branch-and-bound's own runs as well as
  the seeder's.
- A solve that is cancelled mid-run reports `getRemainingSearchSpace()` for that run, not for the whole
  solve: each run has its own `SearchProgress`.
- A proof that already took many fails now costs up to ~4.3x the work to reach, which is the price of
  the improvements above and is visible as the 2-3x slowdowns listed.
- Restart nogoods (recording each finished subtree as the run unwinds) are not built. They are an
  efficiency device and not part of the proof, and would be measured separately, with the caveat that
  [ADR-0030](0030-nogood-learning-is-not-cdcl.md) found conflict nogoods essentially never fire.
