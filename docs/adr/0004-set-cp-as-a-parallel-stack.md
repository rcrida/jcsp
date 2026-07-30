# 0004. Set-CP support as a parallel domain/constraint/solver stack

**Status**: Accepted

## Context

Adding set-valued CSP variables (`Variable<Set<E>>`, e.g. CSPLib's Social Golfers / Steiner Triple
System / BIBD-style problems) needs a domain representation that supports narrowing without
enumerating every candidate subset — the same "interval, not enumeration" idea `BoundedDomain`
already provides for continuous numeric variables, but over the subset lattice instead of the real
line. The obvious shortcut is to make the new domain type extend `BoundedDomain` and reuse its
narrowing/consumer machinery.

## Decision

Make `SetBoundedDomain<E>` a **sibling** interface to `BoundedDomain`, not a subtype: a `Domain<Set<E>>`
extension exposing a "set interval" (`getLowerBound()`/`getUpperBound()` under subset ordering) plus
an independent cardinality range (`getMinCardinality()`/`getMaxCardinality()`). `SetIntervalDomain`
is the sole implementation. The narrowing methods (`withLowerBound`/`withUpperBound`/`withCardinality`)
return the self-typed `SetBoundedDomain<E>`, matching `BoundedDomain#withBounds`'s own choice, so a
propagator chaining several narrowing calls within one `propagate()` pass never needs an
intermediate cast.

This decision cascades into a genuinely parallel stack rather than a shared one:
- A separate build-time whitelist, `SET_COMPATIBLE_CONSTRAINTS` (`SubsetConstraint`,
  `DisjointConstraint`, `IntersectionCardinalityConstraint`, `PartitionConstraint`,
  `GroundNogoodConstraint`, `SetBoundsNogoodConstraint`, `SetMembershipConstraint`), the set-CP
  analogue of `CONTINUOUS_COMPATIBLE_CONSTRAINTS` (ADR-0006).
- A separate nogood shape, `SetBoundsNogoodConstraint`, the set-CP analogue of
  `RangeNogoodConstraint` (ADR-0002).
- A separate terminal-search stage, `SetBranchingSolver`, wired into *both* chains (ADR-0001)
  because — unlike a continuous domain's single-shot midpoint snap, which is safe because
  propagation-narrowed continuous bounds are typically box-consistent — an arbitrary choice among a
  set variable's undetermined elements has no such guarantee, since set constraints are inherently
  combinatorial, not smooth.

## Rejected alternatives

- **`SetBoundedDomain extends BoundedDomain`.** Rejected: every existing `BoundedDomain` consumer
  (`NumericBounds`, `BisectionConditioningSolver`, interval-arithmetic narrowing, midpoint bisection,
  `doubleValue()` bounds extraction) assumes `Number` semantics that a `Set` doesn't have. Making
  `SetBoundedDomain` a subtype would have meant either breaking those assumptions or adding defensive
  type checks at every one of those call sites to exclude the new, non-numeric case.
- **A general `SetIntersectionConstraint(A, B, C)` materialising an intersection as its own
  variable.** Considered while building `IntersectionCardinalityConstraint` and deliberately not
  built — no confirmed use case needed to query the intersection set itself (checked against both
  Social Golfers and Progressive Party, which share the "meet at most once" shape). Matches this
  project's precedent of building the general/global version of a constraint only once a real need
  is confirmed, not speculatively.

## Consequences

- Extending set-CP support to a new constraint means adding it to `SET_COMPATIBLE_CONSTRAINTS`
  explicitly (ADR-0006), the same discipline as continuous support.
- `SetIntervalDomain`'s own narrowing has a domain-intrinsic tightening rule independent of which
  constraint is doing the narrowing (once `|lowerBound| == maxCardinality`, `upperBound` narrows to
  intersect `lowerBound`; once `|upperBound| == minCardinality`, `lowerBound` widens to union
  `upperBound`) — implemented as intersection/union, not a blind overwrite, after an earlier blind-
  overwrite version silently discarded a genuinely narrower caller-supplied value and masked a real
  infeasibility (see `feedback_bound_tightening_intersection_not_overwrite` in project memory).
- A `Comparator<E>` is a required (not optional) component of every `SetIntervalDomain`, chosen over
  a nullable/`Optional` comparator specifically to eliminate null-handling from every consumer, not
  just to avoid one fallback branch.
