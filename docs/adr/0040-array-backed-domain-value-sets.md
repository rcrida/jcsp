# 0040. Array-backed domain value sets

**Status**: Accepted

## Context

Every set-backed `DiscreteSetDomain` held its values as `Collections.unmodifiableSet(LinkedHashSet)`,
and `DiscreteSetDomain` derives `contains`/`size`/`stream`/`asCollection`/`toList` from that one
`values()` method. Arc consistency reads `D_i × D_j` value pairs per revision, so traversing a
domain is the solver's innermost loop — and that representation charged for it twice.

JFR, on the two Taillard instances after the throughput work of commits `13e4fa2`..`ece2eb7`:

| cost | `Taillard-js-015-15-0` | `Taillard-os-04-04-0` |
|---|---|---|
| `Collections$UnmodifiableCollection$1.hasNext`/`next` (self) | 11.7% | 13.8% |
| `LinkedHashMap$LinkedHashIterator.nextNode` (self) | 10.1% | 8.6% |
| `LinkedHashSet` construction (`putVal`, `afterNodeInsertion`, `linkNodeAtEnd`, `newNode`, `hash`) | ~21.5% | ~26% |

Two separate taxes. The wrapper turned each `hasNext`/`next` into two virtual calls instead of one,
at a call site megamorphic enough that neither inlines. Underneath it, `LinkedHashMap`'s iterator
chased a pointer per element through nodes that exist only to maintain insertion order — order an
array already has, for free.

And the hash structure the traversal was paying for is consulted by `contains`, never by iteration.

## Decision

`OrderedValueSet<T>`, an immutable insertion-ordered `Set` holding **an array for traversal plus
the membership set traversal never touches**. The two builders and `IntRangeDomain`'s canonical
constructor produce it; every `DiscreteSetDomain` implementor keeps `Set<T> values` unchanged, so
there is no public API change at all.

The array costs one bulk fill, because `handingOver` **takes ownership of the set** rather than
copying it:

```java
static <T> OrderedValueSet<T> handingOver(Set<T> values) {
    return new OrderedValueSet<>(values.toArray(), values);
}
```

That is sound because a spent `DiscreteDomainBuilder` never touches its backing set again, and
asserts as much — the precondition introduced in `98375d9` when `build()` stopped copying. The old
`unmodifiableSet` view shared that same set on the same terms; this shares it *and* snapshots an
array alongside. `IntRangeDomain` hands over its own defensive copy, so a caller-supplied set is
still never captured.

It extends `AbstractSet`, which gives set-semantics `equals`/`hashCode` for free — load-bearing,
because `DiscreteSetDomain.domainEquals` compares two domains by `values().equals(...)` across
concrete domain types. `spliterator()` is overridden to an array spliterator reporting
`ORDERED | DISTINCT | IMMUTABLE`, since `DiscreteSetDomain.stream()` is `values().stream()` and the
inherited one would route a stream back through the iterator.

## Rejected alternatives

- **`Set.copyOf` / `List.of`.** `Set.copyOf` is immutable with a direct iterator, but its iteration
  order is salted per JVM. Value ordering is defined against insertion order and corpus
  reproducibility depends on a fixed seed giving a fixed search ([ADR-0015](0015-seeded-restart-tie-breaking-random-by-default.md)),
  so a salted order is disqualifying, not merely untidy.
- **Dropping the immutable wrapper without replacing it.** Returning the raw `LinkedHashSet` from
  `values()` removes the delegation but publishes a mutable view of a domain's state through public
  API. It also leaves the linked-list traversal and the node-allocation cost untouched.
- **Making the builders array-backed too.** Deferred out of this change, then tried separately and
  rejected; see the follow-up section at the end.
- **An objective-aware `forEach` override.** Written, then removed: nothing in the codebase calls
  `forEach` on a domain's value set, and an override no caller exercises is an unmeasured path.

## Consequences

Interleaved A/B against `88d724d`, 3 reps, nodes explored in a fixed 60s budget: **js 1.21x**
(1.24 / 1.21 / 1.15), **os 1.11x** (1.09 / 1.12 / 1.11).

Semantically inert, and verified as such rather than asserted. Corpus outcomes held at 78 solved,
zero failures, zero `SolutionChecker` mismatches, and node counts were identical on every instance
that ran to completion. Two apparent differences were both chased down:

- `Primes-15-20-2-1` moved 85 → 100 nodes, and measures 85/100/100/100/100/100 across six runs of
  one *unchanged* build — the per-JVM `AC3` arc-order salt, the same noise
  [ADR-0015](0015-seeded-restart-tie-breaking-random-by-default.md) describes and that
  `Domino-300-300` showed during `ece2eb7`.
- `GolombRuler-09-a3` went from `SATISFIABLE` at the 60s wall to **`OPTIMUM FOUND` at 48.22s, on an
  identical 250,004 nodes**. That is the cleanest evidence of inertness available: the same search
  tree, explored fast enough to finish.

The remaining builder-side cost was the largest single item in both profiles after this change; the follow-up section below records what happened when it was attacked.

## Follow-up (2026-10-01): array-backed *builders*, tried and rejected

The decision above left the builders alone, and re-profiling put them next in line: of the samples
inside a `java.util` map or set call, the immediate caller was
`NumericDiscreteDomainBuilder.value` for **22.2%** of a `Taillard-js-015-15-0` solve, and
`DiscreteDomainBuilder.<init>` plus `.delete` for **11.6%** of `Taillard-os-04-04-0`.

So each builder's single `LinkedHashSet` was split into the two halves `OrderedValueSet` wants
anyway — an `ArrayList` for insertion order and a plain `HashSet` for membership — with `delete`
removing only from the set and `build` compacting the list in one pass. `build` then had nothing
left to derive. It was correct (full gate green, including two newly-needed tests for paths
`LinkedHashSet` had been handling implicitly: a repeated value via `of(T...)`, and an add after a
delete).

**It did not pay.** Interleaved A/B against this ADR's own change, 3 reps, nodes in a fixed 60s
budget: `Taillard-js-015-15-0` **1.01x** (1.01 / 0.96 / 1.01), `Taillard-os-04-04-0` **0.96x**
(0.98 / 0.96 / 0.96) — a wash and a small regression.

**The premise was the error.** The cost was read as `LinkedHashMap`'s link maintenance
(`linkNodeAtEnd`, `newNode`, `afterNodeInsertion`), which a list would not pay. But the dominant
term is `HashMap.putVal` — 17.8% on `Taillard-os-04-04-0` — and **both** designs pay it, because
both hash every value for membership. Splitting the structure only moved work: profiling the split
build showed `putVal` down to 10.7% with `ArrayList`/`Arrays.copyOf` up from 0.8% to 2.5%, two
allocations per builder instead of one, and two traversals of the source in the copy constructor
where `LinkedHashSet` made one. Net negative.

What would actually reduce it is not hashing at all: an integer domain could answer `contains` from
a bitset or by binary search over a sorted array. That is a different representation per value
type, closer in spirit to [ADR-0022](0022-bitset-and-residue-arc-consistency-ac3bitrm.md)'s
bit-indexed experiment than to this one, and it is not justified by anything measured here.
