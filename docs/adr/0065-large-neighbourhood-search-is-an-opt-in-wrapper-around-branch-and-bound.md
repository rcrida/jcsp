# 0065. Large neighbourhood search is an opt-in wrapper around branch-and-bound

**Status**: Accepted

## Context

Branch-and-bound improves an incumbent only when its restarts happen to dive somewhere better. On instances too
large to prove, such as `Vrp-A-n32-k5` and `QuadraticAssignment-bur26a`, the best cost after the time limit is
set by how soon the search stumbles on a good region. Large neighbourhood search (Shaw 1998; Perron, Shaw and
Furnon, CP 2004) attacks that directly: fix most of the incumbent, free a small subset, and search the freed part
exhaustively under the objective cut.

## Prototype findings

A prototype inside `BranchAndBoundSolver` was measured on the 85-instance corpus (60s, fixed seed, interleaved).
What it taught:

- A failure budget does not bound time, and a count-based miss throttle starves the search. Scheduling has to be
  by work done, and a restricted run needs its own node cutoff.
- Sharing dom/wdeg weights or the LP gate with the neighbourhood runs lost BinPacking proofs, so a neighbourhood
  run keeps its own selector, LP gate and random stream.
- Freeing random start times and fixing the rest makes a makespan neighbourhood infeasible. A fixing the
  propagation rejects is released rather than abandoning the neighbourhood.
- Two-hop cost expansion for a cost-weighted generator was too slow. One hop is enough.
- BinPacking proofs are a seed lottery; they are judged over nine seeds, not one.

## Decision

Large neighbourhood search is a `SolverConfig` setting, `largeNeighbourhoodSearch`, **off by default**. Switching
it on wraps the optimization chain's `BranchAndBoundSolver` in a `NeighbourhoodSearchSolver`; with it off the
chain is exactly the plain solver and nothing of the feature is on its path.

- `BranchAndBoundSolver` has no knowledge of neighbourhoods. It exposes a package-private `Search` (an
  `improvements` stream, `unrestrictedRun`, `restrictedRun`, `tightened`) which the wrapper drives. A restricted
  run never ends the stream or proves optimality, so an unrestricted run decides that, as before.
- The `neighbourhood` package holds the pieces: `NeighbourhoodGenerator` (`RANDOM`, `COST_WEIGHTED`),
  `LargeNeighbourhoodSearch` (the configuration record), `NeighbourhoodSchedule`, `DecisionSource` (how the
  decisions of an incumbent are found: replay, or declared) and `NeighbourhoodRoot` (fix the rest, releasing
  what propagation rejects).
- Scheduling is **node-counted**, so it is deterministic under a fixed seed: a grace period of unrestricted
  nodes, a share of nodes for neighbourhoods, a patience bound, a generator portfolio weighted by improvements
  per node, and a free fraction that grows when a neighbourhood is exhausted and shrinks when it is cut off.
- The wrapper does not run when the problem has a continuous variable, and it steps aside when the starting
  incumbent is missing until the complete search finds one.
- `COST_WEIGHTED` follows iVRG (Souza, Grimes, O'Sullivan, CP 2024): a tournament of candidate neighbourhoods
  grown through constraint neighbours, scored by objective cost divided by how often a variable was freed.

## Measurements

The production, node-counted version; 60s sweep of the bundled corpus, fixed seed 20260830, default against
large neighbourhood search against default again:

| | default | LNS | default (repeat) |
|---|---|---|---|
| solved | 84 of 85 | 84 of 85 | 84 of 85 |
| optima proved | 25 | 25 | 25 |
| total solve time | 594.4s | 604.6s | 582.4s |

Only two objectives differ: `Vrp-A-n32-k5` 1879 down to 1324 (-29.5%), and `QuadraticAssignment-bur26a`
1,918,131 down to 1,916,787. Total time is +2.8% over the mean of the defaults. The one per-instance gap
(`GraphColoring-3-fullins-4`, 29-33s against 46.7s, a proved optimum either way) is the instance ADR-0064
already found drifting between identical default sweeps. The prototype, gated by wall-clock, reached about
-38% on `Vrp-A` and kept the same 25 proofs.

## Consequences

- Off by default, because it is not free: it spends part of the budget off the complete search, and it helps
  only on instances where the incumbent is the bottleneck. A caller who wants the best cost within a limit on a
  large instance turns it on; `Xcsp3ProblemRunner` and `Xcsp3CompetitionRunner` take `lns` as a fourth argument.
- Generators are pluggable, so a problem-specific neighbourhood is an implementation of one method.
- The defaults in `LargeNeighbourhoodSearch.DEFAULT` were chosen against the corpus; another workload may want
  them retuned, and the node-based gates make a retuning reproducible.
