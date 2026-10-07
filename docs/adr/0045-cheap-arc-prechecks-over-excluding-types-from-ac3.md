# 0045. Give a binary constraint a cheap arc precheck instead of excluding it from AC3

**Status**: Accepted

## Context

`AC3.revise` filters one arc by scanning the domain product: for every value `x` in `D_i`, walk
`D_j` until some `y` satisfies the constraint. That is `O(|D_i| * |D_j|)` per revision, and arc
revision is the solver's innermost loop.

For a disequality the scan is almost entirely wasted. `x` loses support along a `!=` arc exactly
when `D_j` is the singleton `{x}` — so whenever `|D_j| >= 2`, every value of `D_i` is supported and
the revision cannot delete anything, yet the scan re-derives that by walking `D_j` once per value of
`D_i`. `BinaryNotEqualsConstraint.propagate` already encodes the cheap rule and is registered in
`FixpointPropagation.PROPAGATORS`, so AC3's work on those arcs duplicated pruning the fixpoint
performs anyway.

This matters because disequality arcs dominate. Every parser-produced `ne(x,y)` builds a
`BinaryNotEqualsConstraint` (`BinaryComparatorConstraint.of(x, NEQ, y)` throws, so
`notEqualsConstraint` is the only route), and `AllDiffConstraint`'s binary decomposition targets it
too. JFR profiling `GolombRuler-09-a4` — 546 `BinaryNotEqualsConstraint`s over 36 `$dist$`
auxiliaries, from an XCSP3 model that states its distinctness pairwise rather than as one
`allDifferent` — put **27.9% of self time** in `AC3.revise`/`hasSupport`, and
`OrderedValueSet`'s iterator, allocated per from-value by that scan, was the single largest
allocation site in the whole solve at 25.6 GB of 103 GB.

## Decision

`BinaryConstraint` gains an overridable

```java
public boolean mayPruneAlongArc(boolean arcFromLeft, Collection<?> toValues)
```

defaulting to `true`. `AC3.revise` checks it once per revision, immediately after resolving
`isArcFromLeft`, and returns `Optional.empty()` — its own existing "no revision" answer — when it
returns `false`. `BinaryNotEqualsConstraint` overrides it as `toValues.size() <= 1`.

The contract is one-sided: `false` asserts that every from-value has a support, so a wrong `false`
silently loses propagation, while a wrong `true` only costs the scan. `<= 1` rather than `== 1` is
load-bearing — an empty `toValues` supports nothing and must still revise, so that it wipes the
from-side domain out and reports infeasibility.

Nothing is excluded from anything. AC3 still participates in every arc of every binary constraint;
the precheck only lets a constraint that knows the answer decline the scan. Propagation is therefore
unchanged, which the measurements confirm: node counts are byte-identical before and after on every
instance tried.

## Rejected alternatives

**Excluding `BinaryNotEqualsConstraint` from AC3's arc index**, scoped 2026-08-18 for four binary
types and again 2026-09-20 for this one, declined both times. It is unsound as a global filter,
confirmed by 9 test failures in exactly the predicted places: `TreeSolver` calls
`AC3.INSTANCE.revise` directly and runs no `FixpointConsistency` propagator at all, relying on arc
consistency to solve tree CSPs backtrack-free, and `LocalSolver.PREPROCESSORS` contains AC3 but no
not-equals propagator to fall back on. A sound version needs two AC3 instances with distinct
per-`ConstraintGraph` memoization keys. It is also not propagation-neutral even where it is
prune-redundant: AC3 and the fixpoint differ in scheduling, so dropping AC3 changes which propagator
reports a conflict first, hence which nogood is learned and which constraint's dom/wdeg weight is
bumped, hence the search tree. The precheck needs none of that machinery and leaves all four callers
with full arc consistency.

Note the broader reason a per-type exclusion is the wrong shape: of the 15 binary constraint classes
only `BinaryEqualsConstraint` and `BinaryNotEqualsConstraint` are unconditionally arc-consistent in
their own `propagate`. `BinaryComparatorConstraint` no-ops for non-numeric `Comparable` pairs;
`BinaryOffsetConstraint`, `AbsoluteDifferenceConstraint`, `SquareVariableConstraint` and
`DivisionConstraint` are bounds-only and so strictly weaker than AC on domains with holes (for
`x + c == y` with `D(x) = {0, 2}`, `D(y) = {0, 1, 2}`, bounds prunes nothing where AC removes
`y = 1`), and all of them no-op on `NEQ`; `BinaryElementConstraint`, `BinaryLogicConstraint`,
`BinaryPredicateConstraint`, `BinaryReifiedUnaryConstraint` and `BinaryTuplesConstraint` are not
`Propagatable` at all, so AC3 is their only propagation.

**A per-value `hasArcSupport(arcFromLeft, fromValue, toValues)` override** — the same idea one level
in, replacing `AC3`'s private `hasSupport` so a disequality answers in `O(1)` per from-value instead
of `O(|D_j|)`. Built and measured first, then discarded: it is strictly weaker than the precheck,
which also skips iterating `D_i` and rebuilding the domain, and the two together are no better than
the precheck alone (one extra virtual call per surviving revision). Measured on
`GolombRuler-09-a4`, interleaved, 3 seeds, identical node counts in every variant:

| variant | mean wall | vs base |
|---|---|---|
| base | 43.0s | 1.00x |
| `hasArcSupport` only | 38.0s | 1.13x |
| `mayPruneAlongArc` only | 34.2s | **1.26x** |
| both | 34.4s | 1.25x |

**Overriding it for `BinaryEqualsConstraint` too.** Equality's support condition is `x ∈ D_j`, which
is not decidable from `D_j` alone without comparing the two domains — the work the precheck exists to
avoid. `BinaryEqualsConstraint.propagate` already computes the exact intersection, so AC3's scan
there is redundant, but recovering that needs a different mechanism than this one.

## Consequences

`mayPruneAlongArc` is a public extension point on `BinaryConstraint`, so its one-sided contract has
to be stated wherever it is overridden; getting it wrong loses propagation silently rather than
failing a test. It is worth overriding only for a relation whose support condition is decidable from
the to-side domain alone, which is why only one type does.

Measured beyond the instance that motivated it, all with identical node counts:
`GraphColoring-3-fullins-4` 34.6s -> 28.0s (3 seeds), which at a 30s budget is the difference
between `s SATISFIABLE` and `s OPTIMUM FOUND`; `QueenAttacking-06` 3.92s -> 3.12s;
`Taillard-os-04-04-0` neutral; `driverlogw-09` about 2% slower across 3 repetitions, inside its own
7% run-to-run spread, from the one added virtual call per revision on a CSP whose arcs are mostly
not disequalities. That is the trade: a constant per-revision cost everywhere, against removing the
scan on the arcs where it dominates.

After this, `AC3`'s share of `GolombRuler-09-a4` falls from 27.9% to 15.4% and
`FixpointConsistency`'s own worklist bookkeeping — `relevant`'s dedup set and
`ConstraintQueue.queued`, both `Collections.newSetFromMap(new IdentityHashMap<>())` — becomes the
dominant cost at 43.7%. That is the next target, and it is a different kind of problem: not
duplicated propagation but per-call set churn.
