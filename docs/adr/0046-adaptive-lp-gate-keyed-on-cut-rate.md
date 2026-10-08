# 0046. Back the per-node LP bound off when it stops cutting

**Status**: Accepted

## Context

[ADR-0009](0009-joint-continuous-discrete-optimization.md) gives `BranchAndBoundSolver` an LP
relaxation when its objective is a `LinearObjective`, used for three things: a per-node lower bound,
most-fractional branching, and an exact fill of continuous variables at a discrete-complete leaf.
[ADR-0025](0025-lp-model-reuse-across-search-nodes.md) made the per-node cost as low as a retained
structural model allows, and still measured `build` at a fifth of a solve.

On some problems that bound never prunes anything whatsoever. An instrumented solve/cut census:

| instance | LP solves | cuts |
|---|---|---|
| Vrp-P-n16-k8 | 322,995 | **0** |
| Vrp-A-n32-k5 | 49,315 | **0** |
| Fastfood-ff10 | 15,931 | **0** |
| Warehouse-opl | 448 | **0** |
| Knapsack-30-100-00 | 277 | 73 |
| Cutstock-small | 13 | 3 |

`Vrp-P-n16-k8` spends **74%** of its solve inside `LpModelBuilder.solve` for those zero cuts. A sound
frequency-gated probe (skipping a bound forgoes pruning, never admits anything) at a 180s budget:

| LP every | best objective | nodes |
|---|---|---|
| 1 (ungated) | 620 | 1,674,113 |
| 4 | **558** | 4,022,176 |
| 16 | **558** | 6,058,218 |
| 64 | **558** | 6,943,994 |

So the bound is not merely useless there, it is actively costing objective quality. But a fixed gate
is not available: at every-16th, `Knapsack-30-100-00` goes 396 → 192,852 nodes (0.28s → 1.90s). That
blowup is *branching*, not bounding — only 73 of its 277 solves cut, so what a fixed gate destroys
there is most-fractional guidance, ADR-0009's second use.

ADR-0025 prototyped an adaptive gate and rejected it: it gained node count on instances that stayed
UNKNOWN either way, which "is not an outcome measure for an unclosed optimization instance", and
asked for one — "run the non-closing instances to a much longer budget and compare final objective
values". 620 → 558 is that measure.

## Decision

`LpGate` decides per node whether to spend an LP solve, keyed on **a streak of solves that failed to
cut** rather than on a frequency. Every node solves until `PATIENCE` consecutive solves have all
failed to cut; after that the LP runs on one node in `PATIENCE`, and any cut resets to full rate
immediately.

Keying on the cut rate is what makes the trigger mechanical rather than tuned: it engages precisely
where the bound is provably useless and cannot engage where it is working. One constant, not the
three ADR-0025 rejected.

`PATIENCE` is **128**, chosen by sweeping both signals at once:

| PATIENCE | Knapsack-30-100-00 nodes | Vrp-P-n16-k8 objective |
|---|---|---|
| 16 | 486 | 558 |
| 32 | 431 | 558 |
| 64 | 431 | 558 |
| **128** | **396** (= ungated) | **558** |

The win does not depend on the value while the protection does, so the largest is free. At 128 the
protection is provable rather than lucky: `Knapsack-30-100-00` runs 277 solves of which 73 cut, so it
cannot accumulate 128 consecutive misses and the gate never engages. A dead LP pays 128 solves before
backing off, negligible against the 322,995 `Vrp-P-n16-k8` would otherwise run.

A gated node forgoes the bound *and* the most-fractional hint, falling back to the configured
selector. It deliberately does **not** fall through to the plain-objective branch's
`objective.applyAsDouble(assignment) >= incumbent[0]` check, which evaluates a `LinearObjective` over
a *partial* assignment and is not a lower bound once any coefficient is negative. Nothing is lost by
skipping it: `applyObjectiveCut` has already applied the incumbent as a propagated constraint
([ADR-0029](0029-objective-cut-as-a-propagated-constraint.md)) before the node is branched, so the
incumbent stays enforced however the gate decides.

Per-search state, created in `getSolutions` and threaded through the search exactly as the
`AdaptiveVariableSelector` is, for the same reason that field documents: two solves from one solver
must not share one. It is not a `SolverConfig` knob — a measured internal constant, not new API.

Only the bound site is gated. `resolveComplete`'s LP fill constructs a solution rather than bounding
one, and `BoundedFirstSolution`'s is a once-per-probe lower bound, not per-node; both are untouched.

## Rejected alternatives

- **A fixed-frequency gate.** Sound and much simpler, and it delivers the whole `Vrp-P-n16-k8` win —
  but it cannot distinguish an LP that is dead weight from one that is load-bearing, and
  `Knapsack-30-100-00`'s 396 → 192,852 nodes is what that costs. Already rejected by ADR-0025 for the
  same reason; re-measured here rather than taken on faith.
- **A smaller `PATIENCE`.** 16 gives the identical objective but leaves `Knapsack-30-100-00` at 486
  nodes instead of 396, i.e. the gate engaging briefly on an instance whose LP works. Free to avoid.
- **A `SolverConfig` knob for the patience.** More API surface for a constant whose value provably
  does not matter across two orders of magnitude on the instance it is for. Revisit if a real
  workload ever wants the gate off.
- **Making the gate smarter about branching value** — the residual weakness, since a cut-rate
  trigger is blind to the most-fractional hint and `Knapsack-30-100-00` is protected only because it
  happens to cut. An instance whose LP never cuts but whose branching hint is valuable would regress.
  None in this corpus. Left alone rather than guessed at: the signal would have to be something like
  node-count-to-depth, and no instance here motivates designing it.

## Consequences

Verified across **every** corpus instance whose objective is a `LinearObjective` — the gate's entire
blast radius, since it is only consulted inside that `instanceof` and satisfaction instances never
reach `BranchAndBoundSolver` at all. 31 instances, fixed seed, 45s budget, compared on answer,
objective *and* node count:

- **22 byte-identical** in all three, including `Knapsack-30-100-00` at 396 nodes and
  `Cutstock-small` at 64 — the gate never engages where the LP cuts, as designed.
- **2 improved objectives**: `Vrp-P-n16-k8` 667 → **620** and `Vrp-A-n32-k5` 3570 → **3569**, with
  3.5x and 5.2x the nodes. Both still report `s SATISFIABLE` (neither closes), and both reported
  solutions were cross-checked with `xcsp3-tools`' `SolutionChecker` via
  `Xcsp3CompetitionRunner`: `Check OK`.
- **7 with the same objective and different node counts**, all non-closing so node count is
  throughput rather than an outcome: `BinPacking-sum` +27%, `QuadraticAssignment-bur26a` +23%,
  `Taillard-js-015-15-0` +1%, `BinPacking-mdd` −1.7%, `TravellingSalesman-20-30-00` −2%,
  `BinPacking-tab` −5.9%. Node counts move in *both* directions because a gated node also branches
  differently, so the tree is not merely the same tree explored faster.
- **Zero** instances lost an answer, and no objective got worse anywhere.

At a longer 180s budget `Vrp-P-n16-k8` reaches 558 against the ungated 620 — the figure this ADR's
Context opens with. Published optimum for that instance is 450, so a large gap remains; the gate buys
throughput on a bound that was dead weight, not a better search.

What this obligates: the cut census is the thing to re-run if the gate ever looks wrong, and it is
cheap to reproduce — count solves and bound-prunes at `searchCut`'s LP site. And because the trigger
reads only cut rate, any future third use of the LP bound inside `searchCut` would silently become
gated too; a use that must always run belongs outside that branch, as `resolveComplete`'s fill and
`BoundedFirstSolution`'s probe bound already are.
