# ADR-0028: Restart-on-solution for branch-and-bound, built and rejected

**Status:** Rejected (2026-09-21)

## Context

ADR-0026 gave `BranchAndBoundSolver` solution-guided value ordering and closed by naming the
remaining gap: the optimization chain has no restarts at all (`restarts=0` on every instance
measured), while a head-to-head against Choco showed Choco restarting 2-10 times on precisely the
instances jcsp loses worst on. That paragraph is the reason this was tried, and this ADR exists so
it is not tried a second time on the same reasoning.

A restart schedule cannot be used here. `Xcsp3ProblemRunner` infers `OPTIMUM FOUND` from "stream
drained and `cancellation` never fired", so a budgeted restart that ended the stream after a
truncated run would silently claim a proof it does not have — a wrong answer, not a slow one.
**Restart-on-solution** (Choco's `setRestartOnSolution`) avoids that by construction: each restart
searches for one improving solution, so a restart that returns nothing *is* the exhaustive proof.

## Decision

Rejected. Built, measured, reverted.

The implementation worked and was sound: `search`/`searchValues`/`resolveComplete` returning
`Optional<Assignment>` instead of `Stream<Assignment>`, the recursion's `flatMap` becoming a loop
with an early return, and `getSolutions` becoming
`Stream.generate(...).takeWhile(Optional::isPresent).map(Optional::get)` over repeated root
searches. Full gate green first time — 3343 tests, 100% instruction and branch coverage on the
restructured class with no new tests required, javadoc clean. Every instance returned an identical
objective and every `OPTIMUM FOUND` was confirmed by `SolutionChecker`.

It was rejected purely on cost.

## Consequences

Interleaved A/B, three fixed seeds per arm, 60s budget:

| Instance | Before | After | Nodes | Wall-clock |
|---|---|---|---|---|
| `Mario-easy-4` | 3.31s / 21,736n | 3.72s / 27,691n | +27% | +12% |
| `PrizeCollecting-15-3-5-0` | 15.1s / 14,860n | 19.7s / 21,283n | +43% | +30% |
| `Opd-07-007-003` | 6.30s / 53,602n | 6.90s / 59,667n | +11% | +9.5% |
| `ChessboardColoration-07-07` | 24.2s / 902,098n | 24.7s / 965,037n | +7% | +2% |

Eleven of twelve runs slower; the one exception was within noise. No instance faster.

Whole-corpus sweep, 30s budget, both arms on the same machine: **baseline 70 solved, after 69**,
zero failures and zero `SolutionChecker` mismatches in both. The single differing instance is
`driverlogw-09`, which the baseline solved at **29.68s** against a 30s budget and the other arm
missed at 30.35s. It is a *satisfaction* instance — no objective, so `BranchAndBoundSolver` is not
in its chain at all, and its `restarts=31`/`30` are `DomWdegLubySearch`'s own Luby restarts. The
difference is a coin landing either side of the buzzer, not an effect of this change.

So: a uniform 7-43% node tax and 2-30% wall-clock tax, bought nothing.

The mechanism is not at fault — the extra nodes are re-descent, which is exactly what a restart
costs, and nothing in the corpus was in a position to repay it. Restarts help when the search
commits early to a bad region and cannot escape. On these instances the incumbent bound plus
ADR-0026's value ordering already keep it out of those regions, so re-deriving the path from the
root is pure overhead.

## Rejected alternatives

- **A Luby or geometric schedule instead of restart-on-solution.** Ruled out before building, and
  the reason is the load-bearing part of this ADR: it would break the `OPTIMUM FOUND` inference
  described in Context. A caller cannot distinguish a proven optimum from a truncated search if
  the stream can end for either reason.
- **Restarting only on a "significant" improvement.** Would scale the cost down toward zero, but
  the measured benefit is already zero, so it scales that down too. No principled setting exists
  for the threshold either.
- **A `SolverConfig` knob to keep both behaviours.** Two code paths against a 100% branch-coverage
  gate, where the fallback path is the one nothing measures.

## Methodological notes

Three things that cost time here and would cost it again:

- **The solved-count metric is too weak to decide this on.** `Xcsp3CompetitionRunner` scores
  `s SATISFIABLE` as solved, which on an *optimization* instance means only "found one feasible
  solution before the buzzer" — seven instances in this sweep report it at 30.1-30.2s. It also
  scores `s UNSATISFIABLE` as solved, which is how three false UNSATs once hid inside a "68 solved"
  figure. Compare per-instance result classes, not the total.
- **Verify the baseline rather than quoting it.** The figure carried forward in notes was 68; the
  actual same-budget baseline measured 70. Had the 68 been trusted, the after arm's 69 would have
  read as a gain rather than a wash.
- **Do not disable the LP to isolate its cost.** The non-LP pruning path (`objective.applyAsDouble(
  partial) >= incumbent`) is only a valid bound under the non-negative-coefficient assumption
  `BranchAndBoundSolver`'s own Javadoc states. An XCSP3 `maximize` objective is negated into
  negative coefficients, so forcing that path makes the solver prune away the true optimum while
  still reporting `OPTIMUM FOUND` — `Mario-easy-4` returned 165 against the true 545, and
  `PrizeCollecting-15-3-5-0` 16 against 20. It looks like a large speedup and is simply wrong.
  Production never reaches this path for such instances, since the parser always builds a real
  `LinearObjective`.

One correction to the premise this work started from, for the record: Choco does ship an LP
(`org.chocosolver.lp.LinearProgram`, `MILP`), but in 4.10.18 nothing outside that package
references it — it is unwired. Choco bounds the objective with an objective cut instead
(`AbstractIntObjManager` holds the objective as an `IntVar` with `bestProvedLB`/`bestProvedUB` and
a `cutComputer`, tightened on each solution and propagated normally). That makes its per-node
bounding far cheaper than jcsp's per-node LP relaxation (ADR-0009), which is why "Choco restarts
2-10 times" does not transfer into "restarts would help jcsp". It does not, however, explain the
*ratio* above: the penalty is set by the extra node count, which would be similar whatever a node
costs.
