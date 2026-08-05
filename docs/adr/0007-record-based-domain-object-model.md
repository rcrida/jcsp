# 0007. Record-based domain object model

**Status**: Accepted

## Context

jcsp needs to represent several distinct shapes of variable domain (small discrete sets, integer
ranges, continuous intervals, enum values, boolean, set-valued) while letting solver code that only
needs a common capability (enumerability, numeric bounds, set-lattice narrowing) work against a
shared interface rather than a concrete type per domain kind. Naively, each concrete domain would
duplicate `equals`/`hashCode`/`toString` and any shared enumeration or bounds logic.

## Decision

Every concrete `Domain` implementation is a Java record: `IntervalDomain(double min, double max)`,
`ObjectSetDomain<T>(Set<T> values)`, `IntRangeDomain(Set<Integer> values, int min, int max)`,
`EnumDomain<E>(Set<E> values)`, `BooleanDomain()`, `ObjectSingletonDomain(Object value)`,
`AssignmentDomain(Set<Assignment> values)`, `SetIntervalDomain<E>(...)`,
`NumericDiscreteDomain<N extends Number>(Set<N> values)`. Records get `equals`/`hashCode`/`toString`
for free from their components, which is correct here since domain identity genuinely is structural
(same values/bounds ⇒ same domain).

Shared behavior lives in interfaces with default methods, not superclasses (records can't extend a
class): `SetDomain<T> extends DiscreteDomain<T>` declares a single `values(): Set<T>` accessor and
derives every other `DiscreteDomain` method from it by default, so any set-backed concrete record
(`ObjectSetDomain`, `IntRangeDomain`, `EnumDomain`, `BooleanDomain`, `ObjectSingletonDomain`,
`AssignmentDomain`, `NumericDiscreteDomain`) gets `stream()`/`toList()`/`toBuilder()` etc. without
reimplementing them, plus static `domainEquals`/`domainHashCode` helpers so cross-type equality
works (two `SetDomain` instances with the same `values()` are equal regardless of concrete record
type). `NumericDomain<N extends Number> extends Domain<N>` similarly centralizes `getMin()`/`getMax()`/
`withBounds()` for the two domain kinds that can meaningfully expose numeric bounds
(`BoundedDomain` and `IntRangeDomain`) — a fully generic `DiscreteDomain<T>` can't implement it,
since `T` isn't bounded to `Number`.

## Rejected alternatives

- **Hand-written classes with manual `equals`/`hashCode`.** Rejected in favor of records once the
  domain's identity was confirmed to be purely structural — no behavioral state to hide, no
  invariant that a compact constructor couldn't express.
- **A hand-written compact constructor for `NumericDiscreteDomain`** instead of the
  `@Builder(toBuilder = true)`/`@Singular Set<N> values` pattern. Tried first, then dropped once
  disassembling `ObjectSetDomain`'s generated builder (via `javap`) confirmed it already used the
  same `LinkedHashSet`-backed pattern — reusing the existing convention was simpler than a bespoke
  compact constructor doing the same thing.
- **Casting through a raw `BoundedDomain` reference for `T`-typed narrowing calls**, in
  `NumericDomain`/`BoundedDomain#withBounds`. Every real caller (`UnaryComparatorConstraint`,
  `LexConstraint`, `OrderingPropagation`, `BisectionConditioningSolver`) already only had a plain
  `double` in hand, so `withBounds` was changed to take `double` directly rather than a `T`-typed
  pair with a separate `narrow(double,double)` delegating to it via a raw-type cast — eliminating
  every remaining raw type and `@SuppressWarnings("rawtypes")` in that call chain.

## Consequences

- A domain kind that's generic over an arbitrary, non-numeric `T` (e.g. `ObjectSetDomain<T>`,
  `EnumDomain<E>` — used for the `String` golfer names in `Prob010SocialGolfersTest`) cannot
  implement `NumericDomain`, so `NumericBounds`' shared helpers fall back to a stream scan for those;
  this fallback is load-bearing, confirmed by `NumericBoundsTest`'s `ObjectSetDomain`-based gapped-
  domain tests, and can't simply be replaced by a direct `NumericDomain` cast.
- `IntRangeDomain` caches `min`/`max` as extra record components rather than recomputing them per
  call, but this cache is only trustworthy because every non-`of(int,int)` external construction
  path was migrated away (to `NumericDiscreteDomain.of(...)`) first — a domain's cached derived
  state is only as sound as its full set of construction paths.
- Not every `instanceof BoundedDomain` check in the codebase is safe to broaden to the more general
  `instanceof NumericDomain` (used for the O(1)-bounds fast path over `IntRangeDomain` too): several
  checks (`LexConstraint`'s `clipUpper`/`clipLower`, `RangeNogoodConstraint.isSafeToCiteAsRange`,
  `CumulativeConstraint`/`DiffnConstraint`'s output-type selection, `BisectionConditioningSolver.findWidestBounded`,
  `PropagationFixpointSolver.domainSum`) rely on the check meaning "genuinely continuous," not just
  "has numeric bounds" — broadening those would be a correctness bug, not an optimization.
