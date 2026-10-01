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
- **Making the builders array-backed too.** This is where the remaining ~21-26% sits, and it is the
  obvious next increment. It is not in this one because tombstoning or compacting an array under
  `delete` is a real data structure with its own invariants, and bundling it here would have made a
  semantically inert change — verifiable by identical node counts — into one that needed its own
  argument. Deliberately deferred, not overlooked.
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

The remaining builder-side cost is now the largest single item in both profiles.
