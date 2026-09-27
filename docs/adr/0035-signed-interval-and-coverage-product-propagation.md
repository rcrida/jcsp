# 0035. Signed interval arithmetic and EQ coverage for product constraints

**Status**: Accepted

## Context

`ProductConstraint#propagate` and `ProductVariableConstraint#propagate` both opened with the same
guard:

```java
for (int i = 0; i < n; i++) {
    mins[i] = NumericBounds.min(dom);
    if (mins[i] <= 0) return Optional.of(Map.of());   // no narrowing at all
    maxs[i] = NumericBounds.max(dom);
}
```

The whole propagator was gated on *every* factor having a strictly positive minimum, because the
narrowing it performed was `productMin = product of the mins`, `productMax = product of the maxes` —
a shortcut that is only interval multiplication when no factor can be negative. A single factor
whose domain reaches zero turned both classes into no-ops, silently, for every operator.

`LowAutocorrelation-015.xml.lzma` is the pathological case: 105 constraints of the shape
`eq(%0, mul(%1,%2))` where **every** variable's domain is `{-1, 1}`. Every one of its 105
`ProductVariableConstraint`s propagated nothing whatsoever. jcsp explored 1,241,618 nodes in 60
seconds and never found a solution; Choco solved it in 6,646 nodes.

Two separate defects hide behind one guard, and only fixing both helps:

1. **The interval algebra was wrong for signed factors.** `[-4,4] * [-4,4]` reaches `[-16,16]`,
   which neither the product of the mins (`16`) nor the product of the maxes (`16`) reports.
2. **Interval reasoning cannot help this instance even once corrected.** To narrow `x` from
   `x*y == t` you divide by `y`'s range, and `{-1, 1}`'s *hull* is `[-1, 1]`, which contains zero,
   so the quotient is unbounded. The domain excludes zero; its hull does not. Every bounds rule
   gives up regardless of how carefully it is written.

## Decision

A shared package-private `ProductPropagation` helper (the two-sibling rule) carrying both passes,
in increasing strength.

**Signed interval multiplication.** `range(mins, maxs)` folds one factor at a time, keeping the
extremes of the four corner products — exact for any combination of signs, and reducing to the old
product-of-mins/product-of-maxes shortcut when every factor happens to be positive.
`rangeExcluding(mins, maxs, i)` is the complementary product a single factor is divided by, and
`divide` applies the same corner argument to a quotient.

This makes the whole-product feasibility check (`bound` or `target`'s range against
`[productMin, productMax]`) correct for signed domains, and gives `EQ` a genuine two-sided
per-factor rule: `factor == target / (product of the others)`, sound for any signs once
`straddlesZero` has rejected a divisor whose range contains zero.

`LEQ`/`GEQ` keep the strictly-positive gate. Their clips read a single favourable extreme of the
complementary product, which only bounds a factor that cannot itself be negative; generalising them
would mean the same case analysis `EQ` gets through division, and no corpus instance asks for it.

**EQ coverage by enumeration.** When every factor has an enumerable domain and the combinations they
span stay under `MAX_COVERAGE_COMBINATIONS` (50,000), `EQ` instead enumerates the factors' actual
values, keeps each factor value participating in at least one combination the target admits, and
narrows a variable target to the products actually reached. This is exact GAC, and it is the
load-bearing half: four combinations settle `x*y == t` over `{-1, 1}` that no interval rule can
touch.

The precedents are already in the codebase — `ExtremumPropagation#propagateEqCoverage` (added for
the same gapped-domain reason on `MaxVariableConstraint`) and
`SubsetSumCoveragePropagation`'s `eligible`-then-compute shape
([ADR-0023](0023-subset-sum-gac-for-linear-equality-constraints.md)).

## Consequences

`LowAutocorrelation-015`: **187,294 nodes, `s OPTIMUM FOUND` at objective 15**, against a 60-second
timeout at 1,241,618 nodes with no solution at all.

Three branches in the two `propagate` methods became unreachable and were deleted rather than
tested: each `GEQ` pass's `raised.isEmpty()` check, and one `keepTargets.isEmpty()` check. All three
were reachable only when `EQ` ran the `LEQ` clip first and left the domain narrower than `maxs[i]`;
`EQ` now returns from the division pass before reaching them.

Both classes' Javadoc previously advertised the positive-minimum restriction as the propagator's
contract, and `CLAUDE.md`'s builder reference repeated it for `productConstraint`. That restriction
now applies only to `LEQ`/`GEQ`.

## Rejected alternatives

**Tabulating the constraint in the parser instead.** `TabulationRecognizer`
([ADR-0034](0034-tabulating-small-scope-intension-constraints.md)) can compile `eq(%0, mul(%1,%2))`
over `{-1, 1}` into an 8-tuple table with real GAC, and moving it ahead of `ProductRecognizer` was
built and measured: **195,571 nodes**, slightly worse than fixing the propagator, while reordering
the recognizer registry churned unrelated parser tests and left the builder API — every caller of
`productConstraint` that isn't the XCSP3 parser — still silently unpropagated. Fixing the propagator
fixes both callers at once.

**Generalising `LEQ`/`GEQ` to signed factors too.** Their one-sided clips need the same
divisor-based case analysis `EQ` uses, which is a second implementation to cover against a 100%
branch gate for a shape nothing in the corpus produces. Kept gated, documented as such.

**Raising `MAX_COVERAGE_COMBINATIONS`.** The cap is what keeps an exponential enumeration from
running at every node of a search; the corpus instance that motivated the work spans four
combinations, three orders of magnitude below the cap. Nothing measured wanted it higher.
