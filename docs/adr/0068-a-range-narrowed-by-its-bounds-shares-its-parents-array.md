# 0068. A range narrowed by its bounds shares its parent's array

**Status**: Superseded by [0070](0070-an-ascending-domain-narrowed-by-its-bounds-shares-its-parents-array.md)

## Context

`IntRangeDomain` held its values as a boxed array (an `OrderedValueSet`, [ADR-0040](0040-array-backed-domain-value-sets.md))
so that traversing a domain allocates nothing. Narrowing it by its bounds went through
`NumericDomain#withBounds`, which rebuilt the surviving values one at a time into a
`LinkedHashSet` and handed that to a new `NumericSetDomain`.

For a wide domain narrowed a little, that is a hash entry, node and array slot for nearly every
value, on every variable it happens to. The largest Fischer series instances declare about 23,000
variables over `-10000..10000`. Preprocessing narrows most of them through
`MaxVariableConstraint`, and the solver ran out of a 3 GB heap on all 19 such instances. On
smaller Fischer instances the same copying cost time rather than memory.

## Decision

`OrderedValueSet` can be a *slice*: an offset and a length into an array, flagged when its
elements are consecutive ascending integers. `IntRangeDomain.of` builds the boxed array once.
`NumericDomain#withBounds` narrows any domain whose values are such a range by slicing it, which
costs one small object however many values survive. Iteration is still an array walk, in the same
ascending order. A range's `contains` is a bounds check, and two ranges compare equal, and hash,
from their bounds alone.

The result is a `NumericSetDomain` over the slice, not an `IntRangeDomain`. Narrowing a numeric
domain already produced a `NumericSetDomain`, so the solver's hottest call sites see the same
domain types as before, and `IntRangeDomain` remains a root domain. A slice has no gaps: the first
value the builder removes, as arc consistency does, filters the slice into an ordinary array as
before.

The `IntRangeDomain` record keeps its `(values, min, max)` components, and its constructor keeps a
range that already has those bounds instead of copying it. Equality stays set-based, so a range
equals a `NumericSetDomain` holding the same values.

## Rejected alternatives

- **Return the slice as an `IntRangeDomain`.** That made `IntRangeDomain` a third domain type
  commonly reaching `AC3.revise` and `BinaryNotEqualsConstraint.propagate`, besides
  `NumericSetDomain` and `NumericSingletonDomain`. With identical search, `GolombRuler-09-a4` was
  consistently 7% slower (21.7 → 23.4 s over three interleaved runs each). Returning a
  `NumericSetDomain` restored it (21.8 s) and kept every Fischer gain. That variant was much faster
  on `GraphColoring-queen6-6` (12.6 → 7.3 s), also with identical search. That too was the JIT
  inlining differently around the extra type, not less work, so it was not a reason to keep it.
- **Slice in the builder too**, so a deletion that only trimmed an end returned a slice. On
  `color_X2` (500 variables, 12,458 binary `≠` constraints) that was consistently 6–7% slower with
  identical search, for the same reason. The Fischer gains all come from `withBounds`.
- **A record of just `(min, max)`.** `values()` would then have to box each value on every
  traversal, or fetch it from a global cache of boxed integers that only grows. It would also
  break the public record.
- **Filter `withBounds` into an array instead of a hash set.** That is about 4 bytes per surviving
  value instead of a hash entry, but still a copy per narrowed variable. A slice is O(1).
- **Collapse every gapless `NumericSetDomain` into a range.** A range iterates in ascending order,
  and a gapless set built in another order would change value order and therefore search.
  Equality is set-based, so this canonical form is not needed for correctness.

## Consequences

- All 19 Fischer instances that ran out of memory now run to their time limit.
- Measured with interleaved runs against the previous build, three each, with identical search:
  - Fischer-04-06-fair: 10.94 → 8.25 s.
  - Fischer-05-02-fair: 5.59 → 3.30 s.
  - GolombRuler-09-a4: 21.74 vs 21.83 s, unchanged.
  - color_X2: 16.69 vs 16.40 s, unchanged.
- Across the 111 competition and series instances at 30s, run against the previous build:
  - Outcomes and node counts are identical, apart from instances finishing near the time limit
    and run-to-run variation in arc order.
  - The geometric mean time over the 29 instances proved above 0.5s is 0.956.
- A domain whose values have gaps is still narrowed by `withBounds` value by value into a hash
  set, as before.
- An `IntRangeDomain` built through its public constructor from an arbitrary set holds a copy,
  not a range, and `withBounds` filters it the same way.
- `OrderedValueSet#slice` is only defined on a range; any other set is never a slice, so its
  offset is always zero.
- The XCSP3 parser still shares one root `IntRangeDomain` per declared range. Moving that sharing
  into `IntRangeDomain.of` needs a weakly referenced, thread-safe cache, and is left as a
  follow-up.
