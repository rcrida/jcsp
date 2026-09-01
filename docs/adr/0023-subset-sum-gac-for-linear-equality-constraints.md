# 0023. Subset-sum GAC for linear equality constraints

**Status**: Accepted

## Context

Auditing why 21 of 85 bundled XCSP3 competition-corpus instances timed out under a 20-second
budget (via `Xcsp3CompetitionRunner`), one instance — `MarketSplit-01` (30 boolean variables, 4
`EQ` linear equalities, coefficients 3–99, targets 644–860) — is a classic Cornuéjols/Dawande
"market split" instance, a class of problem specifically constructed in the OR literature to defeat
LP relaxation and ordinary propagation. Auditing all 21 confirmed none reached the unpropagated
`PredicateConstraint` fallback the XCSP3 parser's `ConstraintRecognizer` chain uses for genuinely
unrecognized shapes; the real gap was that `LinearBoundConstraint`/`SumBoundConstraint` (the
classes `MarketSplit-01` and `Vrp-A-n32-k5`, another timeout, are built from) only ever got
**bounds consistency** for `EQ`: a `totalMin`/`totalMax`-vs-`bound` check plus per-variable interval
narrowing, both blind to gaps in a discrete domain's actual value set. A gapped domain can make this
understate infeasibility — `x1 ∈ {0,3}, x2 ∈ {0,5}, x1+x2==4`: the achievable range `[0,8]` contains
`4`, but no real combination of live values sums to it — a case only found today by search
eventually exhausting both variables' domains, not by propagation.

`LinearBooleanBoundConstraint` already gets true domain consistency for the same shape, for free:
a boolean domain has only 2 candidate values, so testing both directly against the rest of the
sum's achievable range already is full generalized arc consistency (GAC) — no separate algorithm
needed. General discrete domains (`IntRangeDomain`/`NumericSetDomain`) with more than 2 values have
no such shortcut and had no equivalent treatment at all.

## Decision

Added `SubsetSumCoveragePropagation` (`constraints.nary` package, package-private), a
forward-backward dynamic-programming pass giving `LinearBoundConstraint`/`SumBoundConstraint`'s
`EQ` case real value-level GAC, modeled directly on two existing precedents in this codebase:
the split between a pure-array computational core and a `Domain`-level wrapper that
`ExtremumPropagation` already uses for `MaxVariableConstraint`/`MinVariableConstraint`'s own
`EQ`-coverage pass, and the forward-backward reachability-array technique `RegularConstraint`
already uses for automaton-state reachability — here, reachable partial *sums* instead of
reachable *states*.

`forward[i]` is the set of sums achievable using terms `0..i-1`; `backward[i]` is the mirror image
using terms `i..n-1`; each stored as a `BitSet` with a local per-position offset (`cumMin[i]`/
`sufMin[i]`, computed the same cheap way `LinearBoundPropagation` already computes its own
`minContribs`/`maxContribs` for the bounds pass — no duplicate domain scan). A candidate value `v`
for term `i` is GAC-supported iff `bound - coeffs[i]*v` is expressible as `s1 + s2` for some `s1`
reachable in `forward[i]` and `s2` reachable in `backward[i+1]` — tested via a shifted `BitSet`
membership scan, iterating whichever of the two bitsets has fewer set bits.

Scope, deliberately narrow:
- **Integer-family bound types only** (`LinearBoundPropagation.propagateInt`, not
  `propagateDouble`) — the DP needs exact integer sums; `BoundedDomain` (continuous) variables
  never reach `propagateInt` in the first place, since `IntervalDomain` only pairs with `Double`/
  `Float` bound types in practice (confirmed via the existing test suite's own fixture pairings),
  so no separate `BoundedDomain` guard was needed there.
- **`EQ` only** — `LEQ`/`GEQ` already get exact GAC from bounds consistency alone: an inequality
  only needs *some* achievable value to exist, and each variable's own true extreme is always a
  real, currently-present value, so there's no coverage gap the way `EQ` has (the identical
  reasoning `ExtremumPropagation`'s own Javadoc documents for `MaxConstraint`/`MinConstraint`).
- **A single tunable range guard** (`SubsetSumCoveragePropagation.MAX_REACHABLE_RANGE`, `200_000`
  as a starting point): eligibility requires `totalMax - totalMin + 1 <= MAX_REACHABLE_RANGE`,
  falling back to the existing bounds-only pass otherwise. This one cap also bounds live-value
  enumeration cost per variable for free — a nonzero integer coefficient maps each distinct live
  value to a distinct point within that range, so no separate per-domain cardinality guard is
  needed.
- **`SumVariableConstraint`/`LinearVariableConstraint`/`LinearBooleanVariableConstraint`
  (target-is-a-variable siblings) are out of scope** — a different propagation code path
  (`NumericBounds.propagateWeightedSumVsTarget`), left as possible future work rather than doubling
  this change's surface area.

Composed with the existing bounds pass rather than branching either/or: `LinearBoundPropagation.
propagateInt` runs its existing interval-narrowing pass first (unchanged, cheap, still needed for
`LEQ`/`GEQ`), then — when eligible — runs the coverage pass on the now-narrowed domains within the
same `propagate()` call, avoiding an extra fixpoint round.

**Required correctness fix, not optional**: `LinearBoundConstraint`/`SumBoundConstraint`'s
`explainInfeasible` fallback chain was `RangeNogoodConstraint.fromCurrentBounds(...).or(() ->
GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(...)))`. `fromCurrentBounds`
already correctly refuses to cite a gapped discrete domain as a range — exactly the domains this
pass targets — so it already fell through in that case. But `allSingletonReason` is sound only when
*every* cited variable is singleton, and the entire point of value-level coverage GAC is detecting
infeasibility while other variables are still open. Once this pass could produce that situation,
the existing fallback became a live unsoundness risk, not merely imprecise. Fixed by swapping the
fallback for `ValueSetNogoodConstraint.fromCurrentState(...)` — the exact pattern
`MaxVariableConstraint.explainInfeasible` already established for the identical problem in its own
coverage path.

Verified via a hand-constructed regression suite (not brute-force cross-checking, unlike ADR-0016/
0017's flow-based GAC — the reachability argument here is simpler to verify directly by
construction): gapped-domain infeasibility bounds consistency misses, real partial narrowing
including negative coefficients/values, a parity-based 2-variable infeasibility case bounds
consistency provably cannot detect (`2*x1+2*x2==7` over `{0,1,3}` domains — any sum of two even
contributions is even), a 3-variable case where *every* variable individually passes its own bounds
check yet no joint combination exists, the guard-exceeded fallback path, and the
`explainInfeasible` soundness fix with non-singleton cited variables. 100% instruction/branch
coverage achieved without any coverage-motivated dead branches — one candidate defensive check
(`if (pruned.isEmpty())` after narrowing to the DP's own retained-value set) was removed instead of
tested, since it is provably unreachable: `SubsetSumCoveragePropagation` itself already returns
`Optional.empty()` the moment any term's retained set would be empty, so a domain narrowed to that
retained set can never itself come back empty — the same reasoning `ExtremumPropagation.
propagateEqCoverage`'s own Javadoc documents for omitting an equivalent check.

## Rejected alternatives

- **Wire `LpModelBuilder`'s LP relaxation into the satisfaction chain** (currently used only by
  `BranchAndBoundSolver`, the optimization chain's terminal solver, per ADR-0009) as a general
  pruning boost for `LinearBoundConstraint`-heavy satisfaction problems. Rejected specifically for
  `MarketSplit-01`: the whole reason "market split" is a named hard class in the OR literature is
  that these instances are constructed so their LP relaxation stays feasible throughout, so LP-based
  pruning would add essentially nothing for that particular corpus instance — it might still be
  worth doing generally, but doesn't address the motivating case and was left out of this change's
  scope.
- **Lattice basis reduction (Aardal/Lenstra-style, via LLL)** — the actual technique the OR
  literature uses to solve market-split instances reliably. Rejected as disproportionate: a
  genuinely different algorithm family (an integer-linear-algebra subsystem), applicable to only
  this one narrow problem shape, with no generalization to any other constraint in this codebase.
- **Full per-domain cardinality guard alongside the range guard** — considered redundant once
  derived: with a nonzero integer coefficient, a domain's live-value cardinality can never exceed
  the achievable-range cap on its own (each distinct value maps to a distinct point in that range),
  so the single range guard already bounds both costs.

## Consequences

- `LinearBoundConstraint`/`SumBoundConstraint`'s public signatures and behavior are unchanged for
  every already-tight case; propagation only gets *stronger* for `EQ` over discrete domains within
  the guard, never weaker, and is unaffected outside it (guard-exceeded falls back to the exact
  prior bounds-only behavior).
- Two pre-existing tests in each of `LinearBoundConstraintTest`/`SumBoundConstraintTest` had their
  premise invalidated and needed rewriting: `propagate_noChange_returnsEmptyMap` (a case previously
  believed to be at a propagation fixpoint that the new pass now further narrows — `2*x+3*y==12`
  over `{0..6}`/`{0..4}` narrows to `{0,3,6}`/`{0,2,4}`, since not every numeric value in the
  achievable *range* is actually reachable given the coefficients) and
  `propagateWithReasons_onePinned_perVariablePrunedToEmpty_returnsEmptyReason` (previously asserted
  `explainInfeasible` returns no reason at all in a case it now correctly returns a
  `ValueSetNogoodConstraint` for) — both renamed to reflect the corrected, tighter behavior rather
  than the old gap.
- Whether this change makes `MarketSplit-01` itself complete within a given time budget was not
  assumed — verified via a real before/after `Xcsp3CompetitionRunner` run (same bundled corpus,
  same 20-second-per-instance budget): all 64 previously-solved instances still solve (zero
  regressions), one (`PrizeCollecting-15-3-5-0`) improved from `SATISFIABLE` to `OPTIMUM FOUND`
  within the same budget, and `MarketSplit-01` itself still times out (`s UNKNOWN`) — as expected,
  since per-constraint GAC on 4 independent equations doesn't expose the kind of cross-constraint
  infeasibility market-split instances are built to hide, regardless of how tight any single
  equation's own propagation becomes. It does show a real, substantial effect on search shape
  within the same wall-clock budget (`nodesExplored` 294973→76210, `constraintChecks`
  177652143→45595786 in the run measured), consistent with propagation now doing real per-node
  work rather than none — whether that's a net win at a longer time budget is genuinely
  undetermined from a single capped-budget run and wasn't further chased. `Vrp-A-n32-k5`'s own
  930 `LinearBoundConstraint`s showed no meaningful change (`nodesExplored` 8572→9112).
- Extending the same treatment to `SumVariableConstraint`/`LinearVariableConstraint`/
  `LinearBooleanVariableConstraint` (target-is-a-variable siblings) remains open future work, should
  a corpus instance motivate it.
