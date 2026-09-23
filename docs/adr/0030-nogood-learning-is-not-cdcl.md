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
  per-conflict learning in exchange for not paying for it.
- **Up to decision levels and backjumping**: antecedent tracking, 1UIP analysis, asserting clauses,
  watched-literal propagation. This is the real fix and a large change; ADR-0002's
  nogood-as-constraint model is upstream of it, since a `Constraint` receives `propagate(domains)`
  rather than literal-watch notifications.

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
