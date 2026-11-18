# 0069. `IntRangeDomain` is only a factory

**Status**: Accepted

## Context

After [ADR-0068](0068-a-range-narrowed-by-its-bounds-shares-its-parents-array.md), what made an
integer range cheap all lived in its values: a range `OrderedValueSet` iterates as an array, tests
membership by its bounds, compares and hashes from its bounds, slices instead of copying when
narrowed, and prints as `[min..max]`. A `NumericSetDomain` over such a set has every one of those
properties. `IntRangeDomain` duplicated `NumericSetDomain` in everything else: the same record
shape `(values, min, max)`, the same narrowing builder, the same set-based equality.

It was also the one place a root domain differed in type from every narrowed domain. The solver's
hot call sites therefore saw it alongside `NumericSetDomain` and `NumericSingletonDomain`.

## Decision

`IntRangeDomain` is a non-instantiable class holding the factory `of(min, max)`, which returns a
`NumericSetDomain<Integer>` over `OrderedValueSet.range(min, max)`. The domain abstraction no
longer has a range type of its own.

The factory keeps its name, so the library's most common call, `createVariable(name,
IntRangeDomain.of(1, 3))`, is unchanged.

## Rejected alternatives

- **Deprecate `IntRangeDomain` and move the factory to `NumericDiscreteDomain.range`.** That
  keeps old code compiling for a release, but it means migrating every use, about 1,900 in the
  tests and the README's first example, to a new name. In return it only defers a break for code
  that names the type, and a factory called `IntRangeDomain.of` still reads naturally.
- **Remove the class outright.** The same break as this decision, with a renamed factory added
  on top of it.
- **Keep the record.** It is a second type for what `NumericSetDomain` already represents, and it
  alone made root domains a different type from every other numeric domain.

## Consequences

- Source-incompatible, released as a minor version on the grounds the README's "Upgrading from
  4.2" gives. Only code that names the type breaks: declaring `IntRangeDomain`, `new
  IntRangeDomain(...)`, and `instanceof IntRangeDomain`. Code compiled against 4.2 must be
  recompiled, because the factory's return type changed.
- Root and narrowed integer domains are now the same type, so the solver's hot call sites see one
  fewer domain type. Measured against the previous build, with identical search:
  - Across the 111 competition and series instances at 30s, the geometric mean time over the 29
    instances proved above 0.5s is 0.975.
  - `color_X2`: 16.32 → 13.63 s over three interleaved runs each.
  - GraphColoring-games120 and -queen6-6: 0.85 and 0.89.
  - Nothing measured slower beyond noise.
- A one-value range from `of(5, 5)` is a one-element `NumericSetDomain`, as it was a one-element
  `IntRangeDomain`, not a `NumericSingletonDomain`.
- Sharing equal ranges across a problem's variables is still the XCSP3 parser's job. A shared
  cache inside `of` remains a follow-up.
