# 0061. Branch-and-bound remembers its deepest path until it has a solution of its own

**Status**: Accepted

## Context

On `BinPacking-{mdd,sum,tab}-n1c1w4a` Choco proves the optimum (5 unused bins) in under a second and jcsp
reached the 30s cap with a feasible answer: `SAT(5)` for `mdd` and `tab`, and `SAT(2)` for `sum`.

The question branch-and-bound had to answer was not the hard part. With "at least 6 unused bins" posted as a
constraint, the satisfaction search that seeds branch-and-bound refutes it at restart 38 (about 2.6s,
13,500 nodes). Branch-and-bound then searched the same problem again and had not finished after 40s and
176,000 nodes, on two seeds. Its own log showed 23 runs, each hitting its cutoff, with cutoffs growing to
16,000 failures; the satisfaction search's complete refutation fits in a restart of about 400.

## What it was not

Each of these was switched off or varied with a temporary property, on the same problem:

- **The restart schedule.** Five settings, from the default `50 * 1.3^k` to a single run with a 100,000-failure
  cutoff: none finished in 40s.
- **The LP bound and its most-fractional hint.** Disabling both gave identical node counts to the node.
- **Restart nogoods.** They shorten the satisfaction search on some seeds (10.8s against 23.0s, 3.8s against
  11.2s) but made no difference on the seed that refutes in 2.6s.
- **A dom/wdeg weight reset every few runs.** One trial of four improved.

## Decision

`BranchAndBoundSolver` calls `PhaseMemory#recordIfDeepest` after every successful inference, as
`DomWdegLubySearch#getSolution` does. Until this search finds an improving solution, the memory holds the
deepest path reached and each run tries those values first. The first improving solution overwrites it
(`recordSolution`), and after that depth can no longer displace it, so the solution-guided behaviour of
[ADR-0026](0026-solution-guided-phase-saving.md) is unchanged. The seeder's solution still does not write the
memory ([ADR-0041](0041-reuse-the-satisfaction-search-for-the-first-solution.md)).

The proof is still the final un-aborted run alone; the memory only changes the order candidates are tried in,
which cannot affect completeness.

## Measurement

Time for the optimizer's stream to drain (a proof of optimality), 40s limit, same seeds:

| | before | after |
|---|---|---|
| `tab`, seed 1 | not done | 8.3s |
| `tab`, seed 3 | not done | 25.7s |
| `tab`, seed 4 | 23.7s | 19.6s |
| `mdd`, seed 1 | not done | 9.0s |
| `mdd`, seed 3 | not done | 27.8s |
| `mdd`, seed 4 | 25.2s | 21.6s |

On seed 2 branch-and-bound had stayed on the seeder's incumbent (−1) for the whole run; with the memory it
improves −1, −3, −4, −5 within 0.35s.

Corpus at 60s, the runner's fixed seed, every solution `SolutionChecker` OK:

| | before | after |
|---|---|---|
| solved | 84 | 84 |
| optimum found | 23 | 25 |
| feasible without proof | 8 | 6 |
| total solve time | 698.7s | 591.0s |

`BinPacking-mdd` and `-tab` now prove 5 in 6.8s and 6.4s; `BinPacking-sum` goes from 2 to 5 without a proof;
`TravellingSalesman-20-30-00` from 130 to 104, its optimum. `Taillard-js-015-15-0` read 1301 against 1325
in the sweep, but three seeds of 40s each give 1318/1327, 1284/1284 and 1284/1284, so that was run-to-run
variance.

## Consequences

`PhaseMemory`'s own documentation records that replaying a deepest path can trap a search in a dead prefix,
which is why the satisfaction search resets it when progress stalls. Branch-and-bound has no such reset. No
instance in the corpus went the wrong way, but one with a long dead prefix could, and the reset would be the
remedy.

`BinPacking-sum` is still unproven: even the satisfaction search does not refute "at least 6 unused bins" on it
within 25s, so it lacks reasoning that Choco has, not search order.
