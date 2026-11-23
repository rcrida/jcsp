# 0074. A constraint that propagates its own decomposition gives AC3 no arcs

**Status**: Accepted

## Context

`ConstraintGraph` turned every `BinaryDecomposable` into its pairwise binary constraints when a
problem was built, and AC3 revised all of their arcs. For `AllDiffConstraint`, `CircuitConstraint`
and `SubCircuitConstraint` that is a not-equals per pair of variables, quadratic in the arity.
`KnightTour2-80` (2022 competition) has one `circuit` over 6,400 successors: building the problem
took 95s and left 5.6GB on the heap, holding 41 million arcs, before any search began.

Those arcs add no pruning. `AllDiffConstraint.propagate` is Régin's GAC, strictly stronger than arc
consistency on the pairwise not-equals. `CircuitConstraint` and `SubCircuitConstraint` remove a
fixed successor from every other node, and because `FixpointConsistency`'s queue re-wakes a
constraint after its own narrowing, that pass runs to a fixpoint, which is exactly arc consistency on
the pairs.

The decomposition does have uses other than propagation: `withVariableSubset` builds it as real
constraints when cutset conditioning cuts a constraint across a variable subset, and local search
scores conflicts per pair.

## Decision

`BinaryDecomposable` gains `propagatesItsDecomposition()`, true for those three constraints over
three or more variables. `ConstraintGraph` keeps such a decomposition out of its arcs; AC3 and the
fixpoint never see it. `getAllBinaryConstraints()` still returns it, built on the first request and
memoized per graph, for cutset splitting and local search. Checks that only need to know whether a
decomposition exists, or whether it contains a given constraint type, go through
`hasBinaryDecomposition()` and `mayDecomposeInto(type)`, which those constraints answer without
building it.

Over two variables the single pair stays an arc: such a constraint can be a tree edge, and
`TreeSolver` relies on AC3 alone.

`LocalSolver.Factory.PREPROCESSORS` now runs `AllDiffConstraint`, `CircuitConstraint` and
`SubCircuitConstraint` propagation. It used to leave AllDiff's GAC out as too costly for a one-off
pass, relying on AC3 over the pairs; with no pairs, local search would lose all of that pruning.

## Rejected alternatives

**Capping the expansion by arity**, giving AC3 the pairs only below some size. The pairs prune
nothing at any arity for these three constraints, so a cap would keep a cost that buys nothing and
add a tuning knob.

**Generating arcs lazily** instead of materializing them. It would fix the memory, but AC3 would
still revise millions of arcs per pass for pruning the constraints' own propagators already do.

**Keeping AC3 the only local-search preprocessor for these constraints.** Measured with
`MinConflicts`/`Tabu` through `LocalSolver` (20 runs, 10 attempts of 2,000 steps, two rounds):
without the pairs and without the propagators, `Sudoku-s01a-alldiff` dropped from 20/20 solved to
2-5/20, because its clues no longer eliminate values from the other cells of their rows, columns and
boxes. With the propagators it is back to 20/20 at the same 2ms median.

## Consequences

Measured against the build before this change, with `Xcsp3CompetitionRunner` at 60s:

| corpus | instances | solve time before | after |
|---|---|---|---|
| competition | 85 | 568.6s | 558.3s |
| series | 26 | 161.5s | 148.0s |
| benchmark/measured | 36 | 542.9s | 521.1s |

No status, objective or checker verdict changed. Node counts changed on a handful of instances, as
ADR-0045 predicts when AC3's arcs change: which propagator finds a conflict first decides which
constraint's weight is bumped. `KnightTour2-80` now builds in 0.23s on 64MB.

Local search over clue-free problems runs faster per attempt (`Ortholatin-005` 395ms to 229ms per
run, `ColouredQueens-07` 313ms to 179ms) with unchanged success rates.

A future `BinaryDecomposable` whose own propagation subsumes arc consistency on its decomposition
should override `propagatesItsDecomposition()` too, along with the two cheap queries. A caller that
needs the pairs must use `getAllBinaryConstraints()`, which is costly for a wide constraint the first
time it is called, never the arc views.
