# 0057. A restart remembers the branches it refuted, and the search keeps doing so only while that pays

**Status**: Accepted

## Context

`ssa-2670-130` (a SAT-shaped, unsatisfiable XCSP3 instance) was the last open case in the bundled
`sat` instances, and the obvious fix, real clause learning, was not needed.
[ADR-0030](0030-nogood-learning-is-not-cdcl.md) records why jcsp's per-conflict learning does nothing, and
[ADR-0054](0054-restart-schedule-is-a-choice-between-diversifying-and-refuting.md) found the instance
needs one long run. Two things changed the picture.

**One long run proves it.** With the restart unit raised to 100,000 (no restarts in effect) it is proved
UNSAT in 7.5 to 10.3 seconds on all of three seeds, in 30,000 to 53,000 backtracks. The default needs more
than 200,000 backtracks and does not finish.

**Choco's restarts are not Luby, and they remember.** Its competition entry point restarts on a failure
count that stays near 50 (`InnerOuterCutoff(50, 1.01, 1.01)`) and sets `setNogoodOnRestart(true)`,
recording the refuted decision path at each restart. On `ssa` Choco 6.0.1 makes 864 restarts and 49,150
failures in 2.0 seconds; 4.10.18, which ADR-0054 quoted, makes 18 restarts and 18,490 failures in 0.8
seconds. Many short restarts amount to a systematic search once what each refuted is kept, and that is
what jcsp's memoryless restarts lack. ADR-0030 built the same idea and measured it neutral on the corpus,
which then had no instance that spends its restarts re-refuting one space.

## Decision

At the restart cutoff, `DomWdegLubySearch` records one nogood per candidate the abandoned path had already
refuted: the decisions above it plus the candidate. The candidate the cutoff interrupted proved nothing and
is left out, and forced values are consequences of the decisions and are not cited, so each nogood is sound.
`RestartNogoods` configures it, and `SolverConfig#restartNogoods` defaults to `RestartNogoods.ADAPTIVE`.

**Short nogoods only.** A nogood cites at most `maxArity` decisions, 8 by default. On `ssa`, caps of 6 to 16
solve five seeds of five at a median of 15 to 18 seconds; no cap solves them too but slowly (median 33.6
seconds, five to ten times the constraint checks); a cap of 1 solves none and caps of 2 to 4 solve four of
five. Without a cap, `qwh-o30-h374-01` is lost: at depth *d* a nogood has *d* literals and the deep ones are
huge and rarely met again, the same self-limiting problem ADR-0030 noted.

**Kept while they score.** Each recorded nogood carries a shared `GroundNogoodConstraint.Hits`, which it
increments when it prunes a domain or refutes a node. After `window` (10,000) backtracks, at the next restart,
the search compares hits per backtrack with `minHitRate` (0.01). Below it, it forgets exactly the nogoods it
recorded, through `NogoodStore#remove`, and stops recording, leaving any nogoods learned from conflicts. The
decision is made once. The count is per search and held by the nogoods themselves, so concurrent components
do not share it.

**Why a hit rate.** No static property of an instance separated the winners from the losers: size, constraint
count and restart count all overlapped. The hit rate did. `ssa` scored 0.32 hits per backtrack (119 per
restart), four times any other instance. The clear winners scored 0.024 to 0.072; the clear losers,
`LangfordBin-08`, `Sat-flat200-00-clause` and `MarketSplit-01`, scored about 0.005. Those are satisfiable,
heavy-tailed instances that solve when a restart happens to draw well, which remembered refutations steer
away from.

**The quick check is structural.** With restart nogoods on, inference already propagates every nogood, so the
search no longer also checks each nogood touching the branching variable against the new assignment. With
thousands recorded that check was 45% of the time on `LangfordBin-08` and changed nothing, the search being
identical node for node. `ConstraintSatisfactionProblem#getStructuralConstraintsTouching` exposes the part
that was already memoised. The fixpoint must therefore contain the nogood propagator whenever restart nogoods
are on, which `SolverConfig#satisfactionPropagatesNogoods` arranges for the satisfaction chain.
Branch-and-bound records no restart nogoods and keeps asking only about conflict learning.

## Measurements

`ssa-2670-130`, 40s, Luby unit 100, four seeds: none solved without, all four with (13.6 to 30.9s).

Whole corpus, 20s, seeds default / 1 / 2, against nogoods off:

| | Fixed (always on, cap 8) | Adaptive |
|---|---|---|
| Solved | 83 / 82 / 82 | 83 / 83 / 82 |
| Mean-time geomean (unsolved = 20s) | 1.030 | **0.983** |
| Lost against off | `MarketSplit-01` (seed 1) | none |

Fixed was worst on `LangfordBin-08`: 4.75 times slower on the mean even with the structural quick check, and
before it a timeout on two of three seeds. Adaptive: 1.96 times slower there, solved on every seed. The production
implementation reproduces these on two seeds (geomean 0.980, none lost or gained), with `PrizeCollecting`,
`driverlogw-09` and `Fastfood-ff10` the clearest gains (0.69, 0.70, 0.76).

The `sat` instances, 4 seeds, 40s: 17 of 18 solved without on every seed, 18 of 18 with; the three
`Sat-flat200` siblings of the one that lost under fixed nogoods run 1.5 to 3 times faster.

## Consequences

- The default solves unsatisfiable instances that spend their restarts re-refuting one space, as `ssa` does.
- A satisfiable heavy-tailed instance that stays under the window keeps its nogoods to the end, which cost
  nothing measurable here.
- The threshold and window were read off this corpus, and the gap they sit in is wide but rests on a handful
  of instances. They are fields of `RestartNogoods`, not constants.
- `ssa` seed 1 scored 0.0215, only twice the threshold.
- The judgement is one-shot and cannot turn recording back on, and instances solved within 10,000 backtracks
  are never judged.
- `bmc-ibm-3-04_X2` and `bmc-ibm-4-04_X2` ran 8 to 13% slower in one comparison; that is within the spread seen
  from run to run, but it was not separated from it.

## Rejected alternatives

- **Always on.** Costs 3% overall and turns `LangfordBin-08` from about a second into a timeout on two of three
  seeds, until the structural quick check and the switch-off.
- **A cap derived from the problem.** `ssa`'s nogoods never exceeded 64 decisions, and caps from 6 to 16 behave
  alike, so nothing in the problem points to 8 over its neighbours. It is a plateau, not a derivation.
- **Choco's restart policy** (a near-constant cutoff of about 50). Not tried: it would change the schedule for
  every instance, and recording alone already solves `ssa`.
- **A larger default Luby unit.** Solves `ssa` but loses the short restarts the satisfiable heavy-tailed
  instances need (ADR-0054); `SolverConfig#lubyUnit` now exposes it for a caller who wants that trade.
- **Real clause learning** for `ssa`. Not needed for it, and no local measurement says it would help the
  others; ADR-0030's reasoning for it still stands as an open option.
