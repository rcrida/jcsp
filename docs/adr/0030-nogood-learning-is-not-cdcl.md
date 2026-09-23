# ADR-0030: jcsp's nogood learning is not CDCL, and the gap explains why it does not pay

**Status:** Accepted (2026-09-23)

## Context

[ADR-0002](0002-nogood-learning-as-first-class-constraints.md) models a learned nogood as a real
`NogoodConstraint` joining the propagation fixpoint. That decision is sound and its stated benefit
— a nogood catches a variable forced to a forbidden value by *another* constraint's propagation,
which an assignment-map comparison never would — is real.

What was never measured is whether the learned clauses do any work. They do not.

**Do learned clauses ever fire?** Instrumenting both firing sites (a clause reporting infeasible,
and a clause pruning a domain) and counting distinct clauses that ever did either:

| Instance | clauses learned | ever fired |
|---|---|---|
| `BinPacking-sum-n1c1w4a` | 8,648 | **0** |
| `BinPacking-mdd-n1c1w4a` | 6,939 | **0** |
| `QuadraticAssignment-bur26a` | 3,062 (2,492 of arity 3) | **0** |
| `driverlogw-09` | 4,831 | 51 |

`driverlogw-09` is the control that proves the instrumentation works, and it is instructive in its
own right: fires cluster entirely in arity 10-50 (2-23% hit rate), with **0% below arity 10 and 0%
above 75**. So there is no arity threshold that would have made the other three instances work —
nothing fires there at any width.

**Does the whole mechanism change any outcome?** Whole corpus, 85 instances, CDCL on versus off:

**71 solved either way — the same 71 instances.** One instance differs, and it differs in favour of
*disabling* learning: `ChessboardColoration-07-07` goes `SATISFIABLE` to `OPTIMUM FOUND` with CDCL
off. Corpus-wide the on arm learns **1,162,392 clauses** to achieve this.

Two instances make the point sharper than the totals do. `Sat-flat200-00-clause` learns 63,800
clauses and explores **exactly** 167,432 nodes with and without them — not approximately, the same
number, so no clause changed a single search decision. `Bibd-sum-06-050-25-03-10` likewise: 1,663
nodes both ways.

## Decision

Stop making incremental improvements to the current scheme. The next change here must be one of the
two structural options below, not a third patch.

The reason is that jcsp implements a *recognisable subset* of clause learning, and the parts it
omits are the parts that make clause learning work. Against the standard CDCL construction (GRASP,
Marques-Silva & Sakallah 1996; Chaff, Moskewicz et al. 2001; MiniSat; Glucose's LBD, Audemard &
Simon 2009) and its CP counterparts (generalized nogoods, Katsirelos & Bacchus 2005; lazy clause
generation, Ohrimenko, Stuckey & Codish 2009):

| Ingredient | jcsp |
|---|---|
| Implication graph / antecedent tracking | absent |
| Decision levels | absent |
| Conflict analysis to the first UIP | absent |
| Asserting clause + backjumping | absent — backtracking is chronological |
| Watched literals | absent — each clause gets a `propagate(domains)` call |
| Deletion by clause quality (LBD/activity) | by **arity**, the size proxy LBD replaced |
| A clause learned per conflict | present |

**In CDCL a learned clause does its main work at the moment it is learned.** The 1UIP cut has
exactly one literal from the conflict decision level, so after backjumping the clause is unit and
immediately forces the opposite value. The clause database is the secondary benefit. jcsp has no
antecedents to resolve backwards through, so no UIP cut is definable, so no clause is asserting,
so there is nothing to backjump to — a learned clause does nothing when learned and can only help
if search later wanders back into the same region.

The zero-fire measurement is exactly what that architecture predicts. The clauses are not bad; they
are structurally incapable of the thing that makes clause learning pay.

`explainInfeasible` is worth naming precisely here: it returns a *snapshot* of current domain state
for some variable subset, not a derivation. That is why it cannot be extended into conflict
analysis without an implication graph underneath it — the information needed was never recorded.

## Consequences

Two coherent directions, and the current design is neither:

- **Down to nogood recording from restarts** (Lecoutre, Saïs, Tabary & Vidal 2007): record the
  decision path at each restart, no implication graph required. Cheap, fits the existing
  architecture, and is what Choco actually runs — see the Choco note below. This gives up
  per-conflict learning in exchange for not paying for it. **Built and measured (2026-09-23) — see
  "The down option was tried" below. It is neutral, not a win.**
- **Up to decision levels and backjumping**: antecedent tracking, 1UIP analysis, asserting clauses,
  watched-literal propagation. This is the real fix and a large change; ADR-0002's
  nogood-as-constraint model is upstream of it, since a `Constraint` receives `propagate(domains)`
  rather than literal-watch notifications. **Not optional as a prerequisite** — see "Backjumping
  needs the same prerequisite" below: even plain conflict-directed backjumping requires it.

Today's scheme pays per-conflict explanation cost for restart-recording's weaker benefit. That
middle is what this ADR rules out.

**What is not established**, and should not be asserted on the strength of this:

- **Whether CDCL costs throughput.** Node-count ratios on instances neither arm solves are not a
  work measure — `QueenAttacking-06` runs 3,478 nodes with learning against 187,464 without, and
  neither finds anything. Distinguishing a throughput win from thrashing needs `constraintChecks`
  alongside node counts, which was not collected. See `feedback_count_is_not_a_cost`: a node count
  is bookkeeping, not work.
- **Seed sensitivity.** The corpus run used one pinned seed per arm. The single class flip
  (`ChessboardColoration`) needs repetition before it counts.
- **Whether learning ever pays here.** `driverlogw-09`'s 51 fires show the mechanism is not inert
  everywhere; the corpus says it does not change an outcome anywhere.

## The down option was tried (2026-09-23)

Restart nogood recording was implemented in `DomWdegLubySearch`: as the Luby budget exception
unwinds, one clause per already-refuted candidate at each level of the abandoned path. Sound because
those candidates were genuinely exhausted; the candidate the budget *interrupted* is excluded, since
nothing about it was proven.

It was reverted. The result is worth keeping because it refines this ADR's own diagnosis.

**The clauses fire.** That was the prediction, and it held. A restart nogood describes the prefix the
next restart is about to retrace — search descends from the root with the same selector and the same
`PhaseMemory` — so it targets the one state most likely to recur, unlike a per-conflict clause
describing a state chronological backtracking has already passed. On `driverlogw-09` the hit profile
went from "arity 10-50 only, 2-23%" to firing across nearly every arity, including 100% at arities
1, 3, 4 and 5.

**Firing did not help.** Whole corpus: 70 solved against HEAD's 71, and the single differing
instance (`MarketSplit-01`) is UNKNOWN in both arms across three separate seeds — a boundary
instance that happens to solve at one seed, not an effect of the change. `constraintChecks` over the
instances neither arm solves: geometric mean 0.982, i.e. neutral. Per-instance the picture is mixed
rather than positive: `driverlogw-09` pays +24% constraint checks for the same node count,
`KnightTour-06-int` trades 18% fewer nodes for 4% more total work, `Steiner3-08` gains ~2.5%, and
instances with `restarts=0` are untouched by construction.

**So "clauses never fire" was a real defect and not the whole story.** Fixing it produced clauses
that do fire and still changed no outcome. Two things this exposes:

- An nld-nogood at depth *d* has arity *d+1*, so on a deep search it reproduces exactly the
  too-wide clauses that do not fire — `qwh-o30-h374-01` learns 3,049 of arity >200. The mechanism
  is self-limiting on the problems that most need help.
- Restart nogoods shift *where* failures are detected: from inference to the cheap
  `isConsistentAmong` check. Only inference failures drain the Luby budget, so the budget drains
  more slowly and the restart schedule silently stretches. `DomWdegLubySearchTest`'s
  `restartsStatisticRecordedIncrementally_notOnlyOnSuccess` caught this as 1 restart where it
  expected 2 — a real semantic interaction, not a test artifact, and one any future attempt must
  account for.

This strengthens the case for the remaining two options rather than settling between them: with the
cheap direction now measured as neutral, what is left is real backjumping or removing learning.

## Backjumping needs the same prerequisite (2026-09-23)

The two options above were framed as "cheap" and "expensive". That framing is wrong, and the
correction matters: **conflict-directed backjumping is not soundly implementable on this codebase
either**, for the same missing ingredient.

CBJ looked like the affordable middle — no clause learning, no implication graph, just a conflict
set per variable and a jump to its deepest member. The information appeared to be already computed
and discarded: `explainInfeasible` returns a `NogoodConstraint` whose `getVariables()` reads like a
culprit set, and this codebase's immutable per-frame `ConstraintSatisfactionProblem` removes CBJ's
usual implementation cost entirely (there is no trail to unwind — returning up the stack restores
state for free).

It is unsound. Counterexample:

```
path: v1=a, v2=b
  v2=b propagates, narrowing v3's domain {1,2,3} -> {2}
  search picks v3, tries 2, fails with reason {v3=2, v1=a}
  conflict set at v3's node = {v1}        (v3 removed; v2 never cited)
  v2 is absent -> CBJ skips v2's remaining values
```

`v2=c` may leave `v3 = {1,2,3}`, and `v3=1` may solve with `v1=a`. The solution is missed.

The nogood `{v3=2, v1=a}` is *sound as a nogood* — that combination really is infeasible. It is not
a complete *conflict set*, because `v2` caused the narrowing that produced the failure without
appearing in it. And it structurally cannot appear: `explainInfeasible` cites the failing
constraint's own `getVariables()`, so it can never name a variable outside that constraint's scope.
A chain of propagations across constraints loses the trail at the first hop.

A conservative conflict set — every assigned variable propagation depended on — is, without
tracking, *all* of them, which is chronological backtracking. So:

- **Graph-based backjumping** (Dechter) is sound without antecedent tracking, jumping to the deepest
  variable adjacent in the constraint graph — but adjacency is only sufficient without propagation.
  Under propagation a non-adjacent variable can narrow a domain through a chain (the same
  counterexample above), so the sound target is the *reachable* set. Measured on this corpus, that
  is the whole problem: `driverlogw-09` 650 variables in 1 component, `qwh-o30-h374-01` 900 in 1,
  `Bibd-sc-06` 1,050 in 1, `Steiner3-08` 27 in 1, `Sat-flat200-00-clause` 600 in 3 with the largest
  holding 99%. A conflict set spanning every variable makes the jump target the immediately
  preceding decision, so graph-based backjumping skips zero levels here. Closed by measurement.
- **Everything else that changes search trajectory needs antecedent tracking**, which is the same
  prerequisite CDCL needs. Having built it, one would build the stronger mechanism.

**A measurement note worth keeping.** A probe was written first — recording, per node exhaustion,
how far a CBJ jump *would* travel and how many untried sibling branches it would skip. It reported
a strong signal (`Sat-flat200-00-clause` 99.2% of exhaustions jumpable, mean 12.3 branches skipped;
`driverlogw-09` 70%, mean 61.2; `Steiner3-08` 2.5%, mean 0.09 as a clean negative control). Those
numbers are real but measure an *unusable* quantity, because the conflict sets they were built from
are not sound to jump on. The soundness condition should have been derived before instrumenting,
not after: a cheap probe run in the wrong order still costs a wrong conclusion.

This simplifies the options rather than adding to them. Only *removing* learning stays cheap — and that is
what was done: see "Acted on" below.

**And the remaining option cannot be justified by a cheap measurement.** The obvious way to decide
whether antecedent tracking is worth building is to measure what sound backjumping would buy first.
That measurement does not exist: a *static* conservative conflict set is the connected component,
measured above as the whole problem and therefore useless; a *dynamic* one — which path variables
actually caused the narrowings that led to this failure — is antecedent tracking itself. So the
experiment that would justify the work requires doing the work.

That leaves this the one decision in this investigation resting on a structural argument and the
literature rather than on a local measurement, which is a weaker footing than everything else
recorded here and should be treated as such. The structural argument is at least clear: the four
mechanisms measured (per-conflict learning, arity gating, GCC explanations, restart recording) all
vary *what is stored*, all are neutral, and the diagnosis says the defect is *where search goes
after a failure*. Nothing that changes that is reachable without antecedent tracking.

## Acted on (2026-09-23)

`SolverConfig#nogoodLearningEnabled` became a tri-state `Boolean` and **the library default is now off**.
`TRUE`/`FALSE` remain an explicit caller choice that always wins; `null` means "library's choice" and is
resolved by the new `SolverConfig#learningEnabled()`. The tri-state exists so this default can change again
without breaking a caller who deliberately asked for one behaviour.

The measurement, whole corpus, 20s budget, CDCL on versus off:

| | |
|---|---|
| Solved | **72 either way**, the same 72 |
| Wall-clock on the 33 instances both solve | geomean **0.859**, median 0.938 |
| Faster without / neutral / slower | **18 / 14 / 1** |

Multi-seed confirmation on the movers: `LangfordBin-08` 15.0s to 5.5-5.8s and `Mario-easy-4` 3.1-3.3s to
1.2-1.5s, both stable across three seeds; `ChessboardColoration-07-07` goes `SATISFIABLE` to `OPTIMUM FOUND`
on three seeds of three.

The one apparent counterexample did not survive repetition. `Sat-flat200-00-clause` — the corpus's only
genuinely SAT-shaped instance, and independently the one this ADR's backjump probe put at 99.2% jump
availability — looked 1.34x slower without learning on a single run. Across three seeds it is 6228/6281/6350ms
with learning against 5772/5513/5861ms without: **faster without, consistently**. So no instance here reliably
benefits, and "CDCL pays on SAT-shaped problems" is untested rather than supported: four corpus instances
carry clause-shaped constraints and only one is a SAT instance.

Learning stays available and fully tested, because "never pays" is a claim about this corpus, not about clause
learning.

**A side effect worth recording.** Turning the mechanism off by default dropped five classes below the 100%
coverage gate — `DiffnVariableConstraint`, `DistinctVectorsConstraint`, `FixpointConsistency`,
`Solver.Factory`'s reason-deriving `Inference`, and `FixpointPropagation.Worklist`. Every one was a CDCL
explanation path covered only *incidentally*, by learning happening to be the default. They now have explicit
tests, which is a better state than before: a supported mode's coverage should not depend on it being the
default. One of those tests also corrected a wrong belief — the second axis pass in
`DiffnVariableConstraint#explainInfeasible` looked unreachable ("cannot separate" seems to imply "compulsory
parts overlap", which the first pass would have caught), but `mandatoryOverlap` additionally requires both
compulsory parts to be non-empty, so a rectangle whose origin range is wide relative to its width reaches it.

## Rejected alternatives

Both were built and measured during the investigation that produced this ADR, and both are rejected
as treating a symptom:

- **An arity gate on learned clauses** — declining to build a clause citing more than a fraction of
  the problem's variables, checked *before* construction at each `(variables, domains)` factory so
  the map copy is never paid. Genuinely effective at what it does: `BinPacking-sum` 18,997 to
  42,315 nodes in a fixed budget, and on `BinPacking-mdd` it took clauses learned from 10,783 to
  22. But it helps by reducing the *cost* of a mechanism that earns nothing, not by improving
  clause quality — any reduction in learning would have done as well, and disabling learning
  outright does better. It also needed a floor constant below which nothing is gated, to stop it
  rejecting clauses on small problems; no principled value for that floor was derivable.
- **A third explanation tier for `GlobalCardinalityConstraint`** (`ValueSetNogoodConstraint`
  after the existing all-singleton and gapless-range tiers), the propagator responsible for 4,032
  of the unexplained wipeouts on `BinPacking-sum`. It worked mechanically — that instance's
  whole-assignment fallback rate went from 75.4% to 8.6% — and changed no outcome on any of the 9
  corpus instances using that constraint. The clauses it produced averaged arity 66 on a
  121-variable problem, which the arity gate above then declined anyway.

## A correction about Choco

ADR-0028 and the head-to-head notes compared jcsp's learning against "Choco's explanation-based and
lazy-clause-generation paths". Checked against the source, that comparison was against machinery
Choco was not running:

- **Lazy clause generation is absent from 4.10.18 entirely** (the benchmarked version — zero
  references). It exists in 6.0.1 but defaults to `lcg = false`, opt-in via `-lcg`.
- **Explanation-based learning** exists in 4.10.18 (`LearnSignedClauses`,
  `ExplanationForSignedClause`) but is gated behind an `-exp` flag defaulting to false, which the
  benchmark never passed; the default learner is `LearnNothing`. Later Choco **deleted** it
  outright (commits "Remove everything related to explanation", "Remove signed clauses").
- What Choco actually ran was `LearnNothing` plus `setNogoodOnRestart(true)` — one nogood per
  restart from the decision path, which is *coarser* than jcsp's per-conflict learning, not tighter.

So Choco matches or beats jcsp on this corpus while doing strictly less clause learning. That is
independent corroboration of the zero-fire result, from a different direction.

One claim in those notes does survive: `setNogoodOnRestart(true)` and `setRestartOnSolution(true)`
are both hardcoded in 4.10.18, so ADR-0028's premise was accurate for the version benchmarked. In
6.0.1 they became `!isLCG()`/`isLCG()`, making restart-on-solution LCG-only.
