# 0036. Derive the redundant cumulative capacity from joint bounds, after parsing

**Status**: Accepted

## Context

`Xcsp3CallbackHandler#buildCtrNoOverlap`'s 2D case posts two redundant `CumulativeVariableConstraint`
axis projections alongside the `DiffnVariableConstraint`, because diffn only reasons pairwise and
cannot catch three or more rectangles collectively overloading a shared strip. A projection needs a
capacity, and there is no "container" in an XCSP3 `noOverlap` to read one from, so `axisCapacity`
derived it from the parsed domains:

```java
maxReach = max_i(origin[i].max + length[i].max);
minStart = min_i(origin[i].min);
return maxReach - minStart;
```

Sound, but on the one corpus instance the projections were added for it is badly loose.
`StripPacking-C1P1` declares `y in 0..19` with heights up to 12, so this reports **31 for a strip
that is 20 tall** — confirmed by reading the constraints back off the parsed model:

```
cumulativeVariable(tasks=16)  capacity=31.0
cumulativeVariable(tasks=16)  capacity=31.0
```

The instance is a zero-slack perfect packing: 16 rectangles totalling exactly 400 units of area into
a 20×20 strip. At capacity 31 over a horizon of 31 the relaxation offers 961 capacity-units for 400
units of energy — 58% slack, on the instance whose entire structure is that the slack is zero.

The real height is stated, just not per-variable: the instance declares `y[i] + h[i] <= 20` for every
rectangle (XCSP3's `le(%0,sub(%1,%2))` strip-control idiom), which caps each rectangle's reach
*jointly* at 20 however wide the two domains are separately. Choco reaches the same bound by
materialising `end[i] = y[i] + h[i]` as a real variable — its model carries 118 variables to jcsp's
80, and the extra ones are exactly these reaches.

## Decision

`recordPairSumUpperBound` collects, as constraints are added, an upper bound on `a + b` for every
**unreified** two-variable `SumBoundConstraint` under `LEQ`/`EQ`. `axisCapacity` then takes the
tighter of that joint cap and the per-variable `origin.max + length.max`. `StripPacking-C1P1` now
derives 20.

Only unreified constraints qualify. A reified `x + w <= 5` may well be false, so it caps nothing —
recording it would be unsound, not merely imprecise.

The whole 2D `noOverlap` is **deferred to `toInstance()`** (`flushPendingNoOverlaps`), mirroring the
`flushPendingSingleValueCounts` pattern already in this class. `PendingNoOverlap` carries the
`XReification` in force when the callback fired, and a new `addOrReify(body, id, reification)`
overload replays it, so the reified case stays a single `AndConstraint` exactly as before.

## Consequences

Corpus unchanged: 75 solved, 0 failed, 0 `SolutionChecker` mismatches, and **no instance changed
status** — only `StripPacking-C1P1` has a 2D variable-length `noOverlap` at all.

It does not solve that instance either: 692,463 nodes at capacity 31 against 1,523,473 at capacity
20, both `s UNKNOWN` at 60 seconds, with the root search space identical at 1.161e53. That was
predicted rather than discovered — timetable filtering only acts where a task has a compulsory part
(`origin.max < origin.min + length.min`), and here every rectangle has `x in [0, 20 - w_min]`, making
the test `20 - w_min < w_min`, i.e. `w_min > 10`, which no rectangle in the instance satisfies. The
projections are structurally inert at the root at *any* capacity. What the tighter bound buys is
cheaper failure: nodes more than doubled in the same wall-clock because conflicts are caught by the
projections' own bounds reasoning instead of deeper in the tree.

So this is a model-correctness fix rather than a measured win, taken on its own terms. Its value is
that the next step — energetic reasoning, which prunes *without* needing compulsory parts and is the
technique zero-slack packing actually calls for — would otherwise have been handed the same
58%-slack relaxation and derived nothing from it either.

Worth revisiting in that light: the sweep-based diffn propagator was declined earlier partly because
the literature finds it "dwarfed" once cumulative constraints are present. That verdict assumed the
cumulative constraints were pulling their weight, which at capacity 31 they were not.

## Rejected alternatives

**Computing the capacity when the callback fires.** Simplest, and it would have worked on this
instance, whose strip constraints precede its `noOverlap`. But XCSP3 fixes no order between
constraint elements, so the tightening would silently fall back to the loose bound whenever a file
happened to declare them the other way round — a correctness-shaped surprise hiding in file layout.
Deferring removes the question entirely.

**Scanning the built CSP's constraints at flush time** (via `builder.build()`) instead of indexing as
we go. Costs a second `ConstraintGraph` construction, and the index is three lines in the one place
every unreified constraint already passes through.

**Materialising `end[i] = origin[i] + length[i]` variables, as Choco does.** A general answer — the
end variable carries the joint bound by construction and any propagator can read it — but it adds two
variables and a linking constraint per rectangle to every 2D `noOverlap`, which is a real cost
against the one bound this needs. Reconsider if a future propagator wants the reaches for its own
sake.

**Tightening `LT` and the other operators too.** `LT` would give `bound - 1` and is a one-line
addition, but no corpus instance states a strip that way, and declining to tighten is always sound.
