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

**A probe's success is a lottery on restart-randomisation position, and that is what the apparent
"formulation" difference actually was.** An earlier version of this section claimed the COP form was
intrinsically harder than the objective-stripped one, because the stripped CSP answered the bounded
question in 7.8s while the probe could not answer it in 60s. That comparison was confounded.

`RestartRandomization.seeded(baseSeed)` closes over a **stateful** driver `Random`, advanced once per
`randomFor` call, and ignores its `restartIndex` argument entirely. So a solve that runs several
searches in sequence hands each later search a different tie-break stream than it would get running
first — and this instance's difficulty varies enormously with that stream (517 to 21,640 nodes across
four base seeds, measured on the satisfaction chain). Solving the **identical** problem three times
from one shared `seeded(20260830)`:

| draw from the shared driver | result | nodes |
|---|---|---|
| first | SATISFIABLE, 7.7s | 8,770 |
| second | SATISFIABLE, 21.6s | 20,873 |
| third | inconclusive, 60.0s | 56,453 |

The probe runs after the initial unbounded search, so it draws from a later position; instrumented in
the real path it spends **42,461 nodes in 55.7s** on a question that the same search answers in 8,770
nodes from the first position. Nothing about the formulation, the constraint set (identical at 271
constraints, diffed), the domains (same fixpoint, 121,986), the propagator list (identical, printed)
or the problem assembly (the probe-assembled CSP solves in 7.7s standalone) differs.

Two consequences. The probe's budget cannot be sized meaningfully while its workload is a lottery —
8,770 nodes suffices from a lucky position and 56,000 does not from an unlucky one, which is why
`probeRestartBudget` looked marginal rather than wrong. And "same seed" no longer implies "same
behaviour" for any individual search once a solve runs more than one, which it now always does.
Making `randomFor` a pure function of `(baseSeed, restartIndex)` through a mixer — keeping the
anti-correlation property ADR-0015 wanted, without the statefulness — would fix both, and would also
make `IndependentSubproblemSolver`'s concurrent draws deterministic, a caveat that class's Javadoc
currently carries. Not done here.

## Correction: a bound narrowed into the domains is not a bounded probe

*2026-10-09.* Everything above applies each probe's bound by **narrowing it into the domains once**,
before the probe's search starts. That is enough only when the bound lands somewhere narrowing can
see it, and `Taillard-js-015-15-0` — the instance the section above diagnoses at length — is the
favourable case: its objective is the parser's single `$max` auxiliary, so one pass removes 11,000
values. The claim "not missing propagation" was true of that instance and does not generalise.

`TravellingSalesman-20-30-00` minimises `Σ d[i]` over twenty variables with domains `{1..30, 33, 36}`.
The first solution costs **324**, and the LP lower bound is **20** — `Σ min d[i]`, because nothing
linear links the tour to the distances: the lookup tables are not linear constraints, `allDifferent`
is not, and [ADR-0020](0020-assignment-relaxation-for-gcc-linked-tables.md)'s assignment relaxation
declines three times over (it keys on `GlobalCardinalityConstraint`, the tables hold 380 tuples
against a cap of 64, and each shares *two* variables with the all-different group where the linkage
admits one). So probe 0 targets 286. One propagation pass of `Σ d ≤ 286` bounds each `d[i]` at
`286 − 19 = 267`, far above its own maximum of 36, and narrows **nothing**: the probe's preprocessed
domain-size sum is 1040, identical to the unbounded search's 1040.

The probe therefore re-asked the unbounded question, answered it no better than the incumbent, and the
`cost >= bestCost` guard ended the descent after a single probe. Every optimization solve whose
objective is spread across many variables had been paying for a descent that could not descend.

### The fix

`ObjectiveCut` grows `enforce`, which adds the cut to the **constraint set** instead of propagating it
into the domains once. Two halves, both required:

- the cut as a real constraint, so every node's fixpoint re-derives it against the domains *that node*
  has narrowed — which is where a thin bound eventually bites;
- the probe's `FixpointPropagation` filtered for the **cut** problem rather than the original.
  `Factory#forProblem` filters on the constraint types present
  ([ADR-0012](0012-per-csp-propagator-filtering.md)), and its own Javadoc states the assumption this
  breaks: "constraints are never added or removed during search". A `LinearBoundConstraint` added to a
  problem that had none would otherwise sit in the constraint set unpropagated — and
  `LinearBoundConstraint#isSatisfiedBy` returns `true` until every variable is assigned, so it would
  not even prune as a check.

`BranchAndBoundSolver` keeps `narrow`. It applies its bound at **every node**, where a constraint-graph
rebuild and a fresh propagator filter per node would cost far more than the thin-bound passes it gives
up; and by the time its search is deep enough for a sum bound to matter, most objective variables are
singletons and narrowing works. So the two callers now apply the same cut two different ways, which is
the thing to keep straight about `ObjectiveCut`.

Two guards went away as structural consequences. The `cost >= bestCost` check is unreachable once the
cut is a constraint of the problem `ask` validates against: a returned solution costs at most the
target, and one that ignores its bound is rejected by that validation instead. And a `searchCsp == csp`
fast path to reuse the original propagator list, written on the assumption that the unbounded search
gets `csp` itself, was never taken — `seedIncumbent` is reached *below* this chain's own preprocessing,
so even that search is handed whatever the preprocessing narrowed `csp` to. The coverage gate found it.

### Measurement

The 30 bundled COP instances (the only ones affected — the seeder is never constructed outside the
optimization chain), 60s each, the runner's own fixed seed. **Result class identical on all 30**: 22
optimum found, 8 feasible without proof, both arms, every solution `SolutionChecker` OK. Total solve
time 573.0s → 577.2s, **+0.7%**, spread thin rather than concentrated — 24 instances within ±0.5s and
`GolombRuler-09-a4`'s +1.58s the largest single change. That is the descent doing real work where it
previously aborted after one probe, and it is the whole price.

The gain is in answer quality on the eight that do not prove optimality, across three restart seeds:

| instance | before | after | |
|---|---|---|---|
| `Vrp-A-n32-k5` | 3569, 3569, 3569 | **1640, 1871, 1622** | −48% to −55% |
| `Vrp-P-n16-k8` | 606, 606, 606 | **455, 496, 499** | −18% to −25% |
| `QuadraticAssignment-bur26a` | 2696169, 2862696, 2904938 | **2358989, 2481424, 2745097** | −5% to −13% |
| `BinPacking-mdd`/`-sum`/`-tab` | 5 / 3 / 5 | unchanged | |
| `Taillard-js-015-15-0` | 1284 | unchanged | |
| `TravellingSalesman-20-30-00` | 118 | unchanged | |

Every seed improves on every instance that moves at all, and the before column is stable enough
(`Vrp-A` is 3569 on all three seeds, via nine branch-and-bound improvements each time) that the
change is not seed luck. Both `Vrp` instances now emit **one** `o` line where they emitted nine or
ten: the descent arrives with the answer, which is the `Taillard-os-04-04-0` shape this ADR was
written for, reached on two more instances.

`TravellingSalesman-20-30-00` — the instance that exposed all of this — is **not** among the winners.
Its descent now runs `324 → 242 → 206 → 174 → 152` where it managed one aborted probe before, so
branch-and-bound starts from 152 instead of 324, and still finishes the 60s at 118 against a true
optimum of 104 (confirmed by a Held-Karp dynamic program over the instance's own distance matrix).
The remaining limit here is `probeRestartBudget`: probe 4 is cut off at 32 restarts, while the same
bounded questions asked with an unlimited budget answer at **280 restarts for 110 and 765 for 104**,
the latter in 21.3s. So the trade this ADR recorded as unmeasured — a larger probe budget collecting
answers like those at the cost of lingering on probes that have none — is now the whole gap on this
instance, and is still unmeasured.

### The probe budget, now measured and left alone

The trade this ADR recorded as unmeasured — a larger `probeRestartBudget` collecting answers that 32
restarts just misses, at the cost of lingering on probes that have none — was swept at 32, 128 and
512 over the 30 COP instances. **Both increases are worse overall, and the default stays at 32.**

| budget | optimum found | notes |
|---|---|---|
| 32 | 22 | |
| 128 | 22 | `GraphColoring-3-fullins-4` consistently +50% (25s → 38s), `GolombRuler-09-a4` +12% |
| 512 | **21** | loses `GraphColoring-3-fullins-4`'s proof; `BinPacking-sum` *worse* (3 → 4); `GolombRuler-09-a3` 3s → 24s |

512 is the failure mode the field's own Javadoc predicts: one unanswerable probe consumes the budget
branch-and-bound would have spent, so an instance that proved optimality stops proving it and another
returns a worse objective than it did with a smaller budget. The descent breaks on the first
unanswerable probe, so the waste is bounded at one probe — but on a large instance one probe is
enough.

128 is the interesting case, and it is why this needed five seeds rather than one. On
`TravellingSalesman-20-30-00` a single seed showed 118 → 107, which looked like the whole gap closing:

| seed | 32 | 128 |
|---|---|---|
| 20260830 | 118 | 107 |
| 20260831 | 118 | 122 |
| 20260832 | 118 | 120 |
| 20260833 | 118 | 109 |
| 20260834 | 122 | 118 |
| mean / median | 118.8 / 118 | 115.2 / 118 |

A better mean, an identical median, and the spread widened from 118-122 to 107-122. Budget 128 wins
three of five seeds by larger margins than it loses, so the mean favours it by ~3% — but buying a 3%
mean with that much variance plus a 50% slowdown on an unrelated instance is not a default change.
Four of the five runs at 128 emit a single `o` line, so the descent really is doing the work; it is
just not reliably doing it better.

Two measurement notes worth keeping. Wall-clock on a long batch picks up machine suspension —
`caffeinate -i` blocks idle sleep but not system sleep, and two runs recorded 962s and 360s against a
60s limit while both returned the answer 60s of search gives, so the solver's own reported figures are
the ones to read. And `BinPacking-mdd-n1c1w4a` returns 5, 5 and 2 across seeds at an unchanged
budget, so it cannot carry a per-seed comparison in either direction.

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
