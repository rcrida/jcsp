# 0071. `IntRangeDomain.of` shares a range through a weak global cache

**Status**: Accepted

## Context

`IntRangeDomain.of(min, max)` boxes every value in the range into an array. An XCSP3 array of
thousands of variables over one wide range would build that array once per variable, so the parser
kept its own map from declared range to domain and shared one domain across them.
[ADR-0068](0068-a-range-narrowed-by-its-bounds-shares-its-parents-array.md) and
[ADR-0069](0069-intrangedomain-is-only-a-factory.md) left moving that sharing into `of` as a
follow-up.

Only `buildVarInteger` went through the parser's map. The parser's auxiliary variables, models
built through the API, and `DiffnPropagation` all called `of` directly. `DiffnPropagation` calls it
during search with whatever bounds it has derived.

## Decision

`of` keeps a global `ConcurrentHashMap` from the range's bounds, packed into one `long`, to a weak
reference to the domain it last built. While anything still holds that domain, `of` returns it.
Once it is collected, its entry is removed the next time `of` runs, and the next call builds a new
one. Two threads asking for the same range at once may each build one. Both are equal, and the one
stored later stays cached.

The parser's map is gone; every caller of `of` shares.

## Rejected alternatives

- **A strongly held map.** `DiffnPropagation` asks for ranges it derives during search, so the map
  would hold every range a long-running process ever narrowed to, with its boxed array, forever.
- **A fixed number of slots, indexed by a hash of the bounds.** Its memory is bounded, but it keeps
  whatever ranges last landed in each slot, including large ones nothing uses any more, and a
  collision between two ranges a model uses alternately rebuilds them on every call.
- **Keep the parser's map.** It shared only what the parser declared, and only within one parse.

## Consequences

- Equal ranges are the same object while they are held, wherever they were built. Nothing relies on
  that for correctness: domain equality stays value-based.
- Every `of` call does one map lookup and polls the reference queue. A range nothing holds is
  rebuilt the next time it is asked for. In three interleaved runs each against the previous build,
  `Fischer-04-06-fair`, `Fischer-05-02-fair`, `StripPacking-C1P1` and `Taillard-js-015-15-0` were
  unchanged: identical node counts and times on the first two, and node counts within run-to-run
  noise on the two that ran into their time limit.
- The cache is static state. A test that needs to see an entry collected clears and enqueues it
  through the package-private `cachedEntry`, because a test can't make the garbage collector
  collect a domain on demand.
