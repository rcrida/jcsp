# 0044. Tighten the starting incumbent with bounded probes

**Status**: Accepted

## Context

[ADR-0041](0041-reuse-the-satisfaction-search-for-the-first-solution.md) gave branch-and-bound a
first solution from the satisfaction chain's search, and recorded the obvious weakness: that search
ignores the objective, so the solution it finds is feasible and nothing more. On
`Taillard-js-015-15-0` it lands on makespan **1330** — the loosest value the declared domains permit.

The machinery to do better was already in place. A bound on a `LinearObjective` is expressible as a
real constraint, which branch-and-bound has applied at every node since
[ADR-0029](0029-objective-cut-as-a-propagated-constraint.md), and
[ADR-0043](0043-inconclusive-is-not-unsatisfiable.md) made "proven unsatisfiable" distinguishable
from "gave up" — without which a bounded question's negative answer cannot be trusted.

## Decision

`BranchAndBoundSolver` takes an `IncumbentSeeder` instead of a bare first-solution `Solver`, and
`BoundedFirstSolution` implements it: find a feasible solution, then ask the same search a *bounded*
question — "is there a solution costing at most `target`?" — for a sequence of targets below it.

The bound is applied by `ObjectiveCut`, extracted from `BranchAndBoundSolver` so both share one
implementation of "express a cost bound as a propagated constraint". Propagated, not merely checked:
the measured effect on a job shop is that bounding the makespan removes **11,000 domain values** from
the problem, dropping the domain-size sum from 133,072 to 121,986, because the bound reaches every
operation's latest start through the precedence chain.

The schedule is a **descent, not a bisection**. Between the costs a bounded search can satisfy and
those it can refute lies a band where it can do neither within any sane budget — measured at roughly
1220 to 1274 on that instance, straddling the optimum — and a bisection aims its first probe squarely
into the middle of it. Stepping down by `stepDivisor`ths of the remaining gap keeps the early probes
in the region that answers cheaply, and each answer shrinks the gap for the next.

Three answers, three responses, which is what ADR-0043 bought:

| answer | response |
|---|---|
| satisfiable | adopt it, measuring the next step from its **real cost**, which may be well below the target |
| proven unsatisfiable | raise the lower bound, shrinking every later step |
| neither | stop; the band has no bottom worth hunting for |

A refutation is used **only to aim probes, never to prune**, so even a wrong proof could waste work
without changing the answer. The containment is the design: only the incumbent crosses back into
branch-and-bound, which validates it, and every solution after the first still comes from its own
unrestarted search — so the optimality proof a drained stream represents is untouched.

The four tuning parameters are `@Builder.Default` fields rather than constants, matching
`DomWdegLubySearch`'s own `lubyUnit`/`maxRestarts`. ADR-0028's refusal of a knob does not apply:
that was a flag selecting between two code paths, where these are parameters of one. Making them
injectable is also what let the descent be unit-tested directly rather than only through a solve.

The injected search is wrapped in a `PropagationFixpointSolver`, unlike the satisfaction chain's use
of the same slice. A bounded probe hands over a problem whose bound has only just been narrowed into
one variable's domain; without propagating that first, the search starts from root domains that do
not reflect the bound at all.

## Measurement

Corpus at 60s: **84 solved, 1 unknown, 0 failed** — unchanged in count, every solution
`SolutionChecker` OK, and **nothing more than 3s slower**. The gains are in answer quality and time:

| instance | before | after |
|---|---|---|
| `Taillard-os-04-04-0` | SATISFIABLE, 193 after ten improvements, 60.1s | **OPTIMUM FOUND, 193**, 20.6s |
| `ChessboardColoration-07-07` | 7.4s | **1.3s** |
| `PrizeCollecting-15-3-5-0` | 9.5s | **4.5s** |

Total corpus wall time 867s → 823s.

`Taillard-os-04-04-0` is the case this was built for, working exactly as intended: the descent lands
on **193, the true optimum, as its first emitted solution**, where the unbounded search previously
emitted ten solutions climbing 259 → 255 → 239 → 219 → 217 → 210 → 209 → 195 → 194 → 193 and then had
no budget left to prove it. Starting from the optimum, branch-and-bound proves optimality in 20.6s.

## The instance it was built for, and did not help

**`Taillard-js-015-15-0` is unchanged at 1330**, and the reason is worth recording because it
contradicts the prediction in ADR-0041 that bounding would be the way to close that gap.

Measured against the objective-stripped CSP, bounded feasibility is *easy* on this instance: 1281 is
satisfiable in 3.1s, 1293 in 4.7s, both faster than the unbounded search's own 4.4s for 1330. Through
the probe, the same questions cannot be answered at all. Ruled out by measurement: it is not the probe
budget (512 restarts runs the full 60s without answering), not missing propagation (the bound provably
reaches the decision variables, 11,000 values removed), and not the step size (a probe at 1318, just
12 below a solution already in hand, is equally unanswerable).

An earlier version of this ADR blamed variable ordering: the bound lands on the parser's `$max`
auxiliary, so tightening it shrinks that variable's domain, which would make dom/wdeg branch the
objective auxiliary early, and `maxConstraint(e[], EQ, $max)` being an equality would then demand a
schedule with exactly that makespan. **That explanation is wrong.** Wrapping the selector and
counting its choices over a full solve puts `$max` at **3 picks out of 1,547** — dom/wdeg ranks it
almost last, as its ratio predicts (368/1 against a start variable's ~552/3). There is no
early-auxiliary-branching problem to fix.

Two things are true instead, and they are separable:

**The probe budget is marginally too small.** Running the objective-stripped CSP bounded at 1284
through the probe's exact configuration — `satisfactionSearch` wrapped in a fixpoint — it answers
SATISFIABLE at **8,764 nodes**, and a 32-restart budget gives up at **8,522**. Three percent short.
A larger `probeRestartBudget` would collect answers like that one, at the cost of lingering longer on
the probes that have no answer; that trade has not been measured across the corpus.

**The COP formulation really is harder, for an unestablished reason.** The same bound, same
configuration, same budget of 512 restarts: the stripped CSP answers in 7.8s, while the bound applied
through `ObjectiveCut` on `$max` produces no answer in 60s. After propagation both forms give
`e[i] <= 1284`, so the difference is something else about carrying `$max` and `maxConstraint` — not
the branching order, which has been measured and ruled out. Left open rather than guessed at a third
time.

## Rejected alternatives

**Bisection on the objective.** The textbook schedule, and the measurement above is what rules it
out: from `[963, 1330]` the first probe lands at 1146, cheaply refuted, and the second at 1238 —
inside the band that answers neither way — spending the budget to learn nothing.

**Trusting a negative answer without the ADR-0043 distinction.** An inconclusive probe read as a
refutation raises the lower bound on no evidence and converges below the true optimum: a wrong
answer, not a slow one. The band measured here is where that would have happened, so this was not a
theoretical risk.

**Letting the seeder emit each improvement as its own stream element.** Branch-and-bound would then
report the descent's intermediate solutions as `o` lines. Rejected because the descent is one phase
producing one best answer; emitting its working makes `getSolutions`' progression noisier for no
information a caller can act on, and `Taillard-os-04-04-0` shows the preferable shape — one `o 193`
instead of ten lines walking down to it.

## Consequences

`BranchAndBoundSolver` is smaller than before this work: the cut machinery moved to `ObjectiveCut`
and the descent lives behind `IncumbentSeeder`, so the class holds a field and a five-line
`seedIncumbent` where an earlier draft of this change had four constants, a three-state result type
and two more methods.

Anything implementing `IncumbentSeeder` can now supply the starting incumbent — a local search, a
warm start read from disk, a different solver entirely — without touching branch-and-bound, and
without any of it being able to affect correctness.

The seeding phase costs time that branch-and-bound would otherwise have. The corpus says that is
worth paying (nothing more than 3s slower, 44s saved overall), but it is paid on every optimization
solve, and a problem whose first solution is already optimal pays it for nothing.
