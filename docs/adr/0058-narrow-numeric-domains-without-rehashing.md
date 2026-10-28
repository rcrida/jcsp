# 0058. Narrow numeric domains without rehashing

**Status**: Accepted

## Context

Every narrowing of a `NumericSetDomain` or `IntRangeDomain` went through `toBuilder()`, which
copied all `n` values into a fresh `LinkedHashSet`, tracked the bounds as it went, applied the
deletions, and then handed that set to `OrderedValueSet.handingOver`, which took another array
snapshot of it. Pruning one value out of `n` therefore cost about `n` hash insertions, `n` node
allocations and a `toArray` pass, plus two more scans whenever the deleted value was a bound.

JFR on `TravellingSalesman-20-30-00` (optimization chain, 60s, fixed seed) put that path at about
16% of the run: `NumericSetDomain.toBuilder` 548 of 5,126 samples inclusive, builder `build` 284,
and the builder's `value`/`values`/`<init>`/`delete` 218–266 each, with `HashMap.putVal` the
largest first-frame cost under `value`.

[ADR-0040](0040-array-backed-domain-value-sets.md) records an earlier attempt at this cost that
failed: it split each builder's `LinkedHashSet` into an `ArrayList` plus a `HashSet`, and gained
nothing because both designs still hashed every value for membership. Its closing paragraph names
what would help instead — not hashing at all.

## Decision

A narrowing builder never builds a hash set.

- `NumericDiscreteDomainBuilder.narrowing(values, min, max)` starts from the domain's own
  `OrderedValueSet` and its known bounds. `delete` only records the value; `build` filters the
  source array once, in order, and takes new bounds from the survivors only if a bound was among
  the deleted. It collapses to empty or singleton as `build` always has, and returns the source
  values unchanged when nothing was removed. A `value(...)` call on a narrowing builder first
  applies the recorded deletions to a `LinkedHashSet`, so the builder keeps its old contract for
  the rare caller that adds as well as deletes. A `Set` that is not an `OrderedValueSet` is copied
  as before.
- `OrderedValueSet.ofDistinct(Object[])` is the new factory for the result: an array with the
  membership index left empty. `contains` scans the array when it holds at most 8 elements, and
  otherwise builds a `HashSet` on first use into a volatile field. The build is idempotent, so two
  threads racing on it each produce an equal set and either may win.
- `handingOver` is unchanged, so a domain built by `of(...)`, `value(...)` or `IntRangeDomain.of`
  still carries its eager index.

## Rejected alternatives

- **Replacing the hash lookups with an array indexed by `value - min`.** Avoids hashing for good
  but needs a per-value-type representation, as ADR-0040 already noted; nothing here measured a
  need for it.
- **Applying the same change to `DiscreteDomainBuilder`.** Not measured; the profile that
  justified this was a numeric one.
- **Keeping the membership index eager and only skipping the bounds comparisons.** The profile put
  the cost in the hash `add`, not the comparisons.

## Consequences

Interleaved A/B against the commit before it, seed 12345:

| Instance | Result |
|---|---|
| `TravellingSalesman-20-30-00`, 20s window | 399.7k → 423.7k nodes, about +6%; up in all 3 pairs |
| `bmc-ibm-4-04_X2` | about 16.7s → 15.7s, about 5% faster, 28,395 nodes both |
| `ssa-2670-130` | neutral, about 8.0s, 1,334,876 nodes both |

`TravellingSalesman`'s node counts are a rate, not a check, since an optimization run stops at its
time limit. The two satisfaction instances ran to completion with identical node counts, so the
search itself is unchanged.

The gain is modest because the profile's 16% was never going to disappear entirely: the rest of a
narrowing is the array filter and the allocation of the result.

A narrowed domain's first `contains` over more than 8 values now pays the index build. Nothing in
the measured runs made that cost visible, but a workload that narrows a domain and then queries it
once would pay for the index and use it only once.

The recorded deletions are tested against each surviving value, so a long list made `build` cost
values times deletions: on `Taillard-os-04-04-0`, where a scheduling domain loses a window of start
times at once, `ArrayList.indexOf` with `Integer.equals` was 34% of the profile's samples. Past
`OrderedValueSet.SCAN_LIMIT` deletions the list is now hashed into a set once, and below it the short
list is still scanned, so the common one-or-two-deletion narrowing hashes nothing. That run went from
3.26s to 2.97s with identical node counts.

Once the element constraint asked many `contains` questions of those domains, the membership index
itself showed up: 25.6% of `Taillard-os-04-04-0`'s samples, 82% of that in `HashMap.getNode`/`hash`,
not in building the index. A set of more than a few `Integer`s no more than 64 bits of range per
element wide is now tested by a bit in a `long[]` instead, whether it was built by narrowing or handed
in with its own hash set, which a first attempt missed by consulting the handed-in set first: that
run measured nothing, and a profile showing `HashMap.getNode` still on top is what caught it. Sparse
integers, mixed types and `Long`s keep the hash index. `Taillard-os-04-04-0` went from 1.82s to 1.65s
with identical node counts; `Taillard-js-015-15-0`, `Vrp-P-n16-k8` and `driverlogw-09` did not move
beyond noise.
