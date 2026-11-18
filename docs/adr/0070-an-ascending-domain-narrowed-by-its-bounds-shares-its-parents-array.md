# 0070. An ascending domain narrowed by its bounds shares its parent's array

**Status**: Accepted

## Context

[ADR-0068](0068-a-range-narrowed-by-its-bounds-shares-its-parents-array.md) let `withBounds` slice
a domain whose values are a range. Any other domain was still rebuilt value by value into a
`LinkedHashSet`. Arc consistency turns a range into such a domain the moment it deletes one interior
value, and bounds propagators then narrow it over and over.

A counter on `withBounds` showed how often that happened in 15 seconds of solving:

- `GolombRuler-09-a4`: 7.6 million calls on non-range domains, about 98 million values rebuilt.
- `Taillard-js-015-15-0`: 335,000 calls, about 234 values each, about 78 million values rebuilt.
- `StripPacking-C1P1`: 563,000 calls, about 14 values each.

Every one of those domains was in ascending order, though none was a range: a range keeps its order
when values are deleted from it. No domain in those runs was in any other order.

## Decision

`OrderedValueSet` records its shape when it is built: a range, ascending with gaps, or unordered.
A range is always ascending. Narrowing an ascending set keeps it ascending. Any other set is scanned
once as it is built.

`NumericDomain#withBounds` slices any ascending domain. The values within the bounds are one
contiguous run of its array, found by two binary searches, so the result shares the parent's array
whatever was deleted from it. A slice that isn't a range tests membership against its own bounds
and then the whole set's index, so narrowing never builds an index of its own.

An unordered domain is filtered into an array that the result keeps as it is, without trimming it
or building a hash set. A filtered narrowing from the builder keeps its array the same way.

A set's order is still the order its values were given in. Only values that happen to ascend are
sliced; nothing is sorted.

## Rejected alternatives

- **Slice descending domains too.** A descending array's values within the bounds are also one
  contiguous run. No domain in the measured instances was descending, so it would have added a case
  that nothing exercises.
- **Sort a domain's values when it is built**, so that every domain could be sliced. A domain's
  order is its value order, so sorting would change search.
- **Decide whether a set ascends lazily, on the first `withBounds`.** That needed a mutable,
  volatile three-state field and a race to reason about. Deciding when the set is built costs one
  comparison per value, in a pass that already copies every value into the array.
- **Trim a filtered array to the values kept.** That is a second copy of every surviving value,
  only to save memory at the end of the array.

## Consequences

- Across the 111 competition and series instances at 30 seconds, interleaved against the previous
  build:
  - Outcomes are identical, and so are node counts on every proved instance except
    `Primes-15-20-2-1`, below.
  - The geometric mean time over the 47 instances proved between 0.5 and 29 seconds is 0.991.
  - `Taillard-js-015-15-0` explored 62,407 nodes in 30 seconds against 56,780.
  - `GolombRuler-09-a4` went from 21.92 to 20.86 seconds.
- `Primes-15-20-2-1` explores 91 nodes against 96 with the same seed, consistently on each build.
  Its search depends on the order of a hash collection keyed by identity hashes, which shifts with
  any change to the code. With identity hashes pinned to a constant, both builds explore 20 nodes.
- A slice keeps its root's whole array alive, and a filtered array keeps its unused end.
- [ADR-0068](0068-a-range-narrowed-by-its-bounds-shares-its-parents-array.md)'s note that a domain
  with gaps is filtered through a hash set, and that only a range is ever sliced, no longer holds.
  Its other decisions stand: a slice is a `NumericSetDomain`, and the builder doesn't slice.
