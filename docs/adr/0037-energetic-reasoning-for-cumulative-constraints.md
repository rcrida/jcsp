# 0037. Energetic reasoning for cumulative constraints

**Status**: Accepted

## Context

Both cumulative constraints propagated by timetabling plus an energy-overload check. Timetabling
acts only where a task has a **compulsory part** — where `lst < est + duration`, so that some
instant is occupied whatever the task does. A task whose start window is wider than its own
duration contributes nothing at all.

On `StripPacking-C1P1` that condition is never met. Every rectangle has `x ∈ [0, 20 - w]`, so a
compulsory part would need `20 - w_min < w_min`, i.e. `w_min > 10`, and the largest `w_min` in the
instance is 7. The two redundant projections
([ADR-0036](0036-joint-bounds-for-redundant-cumulative-capacity.md)) were structurally inert at the
root at any capacity — which is why fixing their capacity from 31 to 20 changed the search without
changing what could be deduced.

The existing `energyOverload` check is not subject to that limitation, but it is overload detection
only. Its own Javadoc records why the bound-tightening half was left alone: disjunctive
edge-finding's rule assumes a task either finishes before a set's window or starts after it, which
is exact only when no two tasks can coexist, and porting it to the cumulative case without
re-deriving the theorem risked something plausible-looking and unsound.

## Decision

`CumulativePropagation`, shared by both constraints (which reduce to the same per-task bounds).

For a window `[t1, t2)` it computes each task's **minimum work** — the least it must do inside that
window under any placement its bounds allow — and compares the total against `limit * (t2 - t1)`.
Exceeding it is infeasible. Falling short bounds where a single task can be: with
`rest = capacity - Σ_{k≠i} work_k` as the energy left for task `i`, a start consuming more than
`rest` is impossible, so `i` can be pushed to `t2 - rest/resource`, and symmetrically pulled back to
`t1 + rest/resource` at the other end.

The soundness of that adjustment rests on a fact worth stating rather than assuming, and it is the
reason this is a different argument from the edge-finding rule that was declined: a task's work in a
window rises as it moves in and falls as it leaves, with no second peak. The starts consuming more
than `rest` therefore form **one** contiguous stretch, so finding `i`'s earliest start already over
budget places all of its remaining domain to the right of that stretch. Without unimodality the
deduction would be wrong, because a start far enough left that the task finishes before `t1`
consumes nothing either.

Windows are every `est`/`lst` as a left edge against every `ect`/`lct` as a right edge,
deduplicated. Bounds that cross are not checked for: they narrow the start domain to nothing, which
is how the caller already learns the constraint is infeasible.

Two further inputs were needed before any of this could fire, both found by measurement:

**Energy floors** (`Tasks#minEnergies`). Task energy was `min(duration) * min(resource)`. A
rotatable 2x12 rectangle has both domains reaching down to 2, so that reports 4 against a real area
of 24 — across `StripPacking-C1P1`, 220 units of load for a strip packed to exactly 400 of 400. A
strip filled to the last cell looked 45% empty. The floor comes from whoever knows the two are
linked: `Xcsp3CallbackHandler#taskEnergyFloors` reads each rectangle's area off its rotation table,
the same "consult the instance" move ADR-0036 makes for the capacity.

**A horizon** (`Tasks#horizon`). A floor may only be charged to a task the window provably
contains, and containment used `lst + maxDuration` — `18 + 12 = 30` for a rectangle on a 20-wide
strip, because it can only start at 18 *when* it is 2 wide. The projections now carry the joint
reach bound, which is the number `axisCapacity` already derives for the other axis.

## Consequences

Propagation on `StripPacking-C1P1` is transformed. Over 30 seconds at a fixed seed:

| | before | after |
|---|---|---|
| nodes explored | 333,901 | 430,823 |
| cumulative propagator firings | 21,752 | 72,712 |
| values removed by the cumulative propagators | 118,840 | **1,522,754** |

About ten times the pruning per node, and faster per node rather than slower, since failures are
reached sooner.

**The instance is still not solved, and the corpus is unchanged**: 75 solved, 0 failed, 0
`SolutionChecker` mismatches, no instance changing status. That last figure means less than it
looks: of 85 instances, exactly one (`StripPacking-C1P1`) reaches this code at all —
`Taillard-js-015-15-0` and `Taillard-os-04-04-0` have 1D `noOverlap`, which routes to
`DisjunctiveConstraint`, and no instance uses `<cumulative>`. The corpus establishes no regression
and nothing more.

> **Correction (2026-09-29).** This section originally concluded that the block was **search
> order** — that dom/wdeg decides all 32 size variables before placing anything, which it provably
> does, and that this was therefore why the instance failed. The first half is true and the second
> was wrong. It was never measured, and when it was, it did not survive: five variable orderings
> (positions-first, widest-domain-first, smallest-first, rectangle-at-a-time, and the dom/wdeg
> baseline) all fail, and the two value orderers produce byte-identical node counts. The real cause
> was solution-guided phase saving replaying an uncompletable prefix across every restart; see
> [ADR-0039](0039-discard-the-phase-memory-on-stagnation.md), which fixes it and solves the
> instance. The ordering analysis below is retained because it is correct about dom/wdeg and
> because the injectable selector it motivated ([ADR-0038](0038-injectable-variable-selector-factory.md))
> is what made the refutation possible.

The extra pruning does not convert here, and the ordering is a genuine oddity even though it turned
out not to be the cause. Probing the deepest assignment reached gives `{h=16, w=16, x=12, y=13}` of
80 variables: all 32 size variables are decided before the rectangles are placed.
`DomWdegVariableSelector` ranks by `domainSize / wdeg`, and at the root `w`/`h` score `2/5 = 0.40`
against `x`/`y`'s `20/3 = 6.67`. Worse, `x[i]`'s constraints — its `x + w <= 20` bound, the diffn,
and one projection — are a strict subset of `w[i]`'s, which adds the rotation table and the second
projection. Weights are incremented per constraint, so every unit `x[i]` can ever gain lands on a
constraint `w[i]` shares: `wdeg(w) >= wdeg(x)` under any weighting whatsoever, and with
`dom(w) = 2 < dom(x) = 20`, `w[i]` is preferred at every node forever. Committing to all 16
rotations before placing anything is structurally forced by the heuristic — it simply is not what
was costing the instance.

So this lands as a real strengthening whose payoff arrived one commit later. It is kept because the
reasoning is exact and general, and because it costs nothing measurable.

## Rejected alternatives

**Dropping `energyOverload` now that energetic reasoning subsumes it.** It does subsume it: for a
task-interval window every member's minimum work is its full energy, so the same total is reached
with partial contributions from outside tasks added. But `energyOverload` is `O(n² log n)` against
this pass's `O(n³)` and runs first, so it is a cheap short-circuit, and removing it would rewrite
two `explainInfeasible` citation paths for no gain.

**Checking bound crossings inside the pass.** A first version returned infeasible when an
adjustment pushed `est` past `lst`. Within a single window that can never happen — `rest` is always
at least the task's own minimum work, and by unimodality the minimum work is the smaller of the two
extreme-start works, so both adjustments cannot fire at once. Across windows the crossing shows up
as an empty domain at the caller regardless, so the check was removed rather than left as an
untestable branch.

**Sweep-based diffn propagation.** Declined earlier partly on the literature's finding that it is
"dwarfed once cumulative constraints are present". That verdict assumed the cumulative constraints
were contributing, which before ADR-0036 and this change they were not — so it is worth revisiting,
though behind an ordering fix.
