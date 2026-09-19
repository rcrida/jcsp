# ADR-0024: Propagator worklist instead of a round-robin fixpoint

**Status:** Accepted (2026-09-19)

## Context

`FixpointPropagation` reached its fixpoint by running every propagator in its list in sequence and
repeating until a whole round failed to reduce `domainSum`.

That list is **already filtered per problem** by ADR-0012's `Factory#forProblem`, and filtered hard:
on real corpus instances it holds 2 to 5 entries out of the 66 in `PROPAGATORS`.

| Instance | Applicable propagators |
|---|---|
| `qcp-15-120-00_X2` | 2 — conflict tables, nogoods |
| `driverlogw-09` | 3 — tables, conflict tables, nogoods |
| `Bibd-sum-06-050-25-03-10` | 4 — sum bounds, lex, min-variable, nogoods |
| `Steiner3-08` | 4 — binary comparator, tables, AC3, nogoods |
| `MagicSquare-6-sum` | 5 — binary not-equals, all-diff, sum bounds, AC3, nogoods |

So the round-robin was never sweeping dozens of irrelevant propagators, and this ADR is **not** about
skipping propagators whose constraint type is absent — ADR-0012 already does that. With a list this
short, the per-round costs that remained were the ones that scale with the *problem*, not the list:

- `domainSum` walked every variable's domain **twice per round**, purely as a convergence test —
  4.5% of JFR samples on `LowAutocorrelation-015`, and paid at every search node.
- `changedVariables` walked the whole domain map again, per round, comparing domains by value, to
  produce a single dirty set shared by every propagator in that round.
- Inside `FixpointConsistency`, a nested `while (changed) for (constraint : relevant)` loop
  re-propagated *every* relevant constraint on each pass until a whole pass changed nothing. On
  `driverlogw-09` — 17,447 constraints of one type over 650 variables, so roughly 27 constraints per
  variable — that rescanned hundreds of constraints to find the handful that could still prune.

## Decision

Replace the round loop with a worklist over propagators, and — inseparably — give
`FixpointConsistency` a per-constraint queue.

- Each propagator carries its own accumulated dirty-variable set and is woken only by variables it
  watches. `ConstraintConsistency#variablesCovered` declares that set; `null` means "wake me for
  anything", the safe default, since over-waking costs a call that narrows nothing while
  under-waking would silently weaken the fixpoint. `FixpointConsistency` answers from the
  `byVariable` index it already builds; `AC3` answers with its arcs' `to` sides, the only variables
  its seeded queue can turn into work. `NogoodFixpointConsistency` keeps the `null` default, since
  its nogood set grows as search learns and so cannot be memoized against the constraint graph.
- Termination is the worklist emptying. Both `domainSum` calls per round disappear, and
  `changedVariables` runs only when a propagator actually changed something — detected in O(1) by
  reference equality against the problem it was handed.
- `next()` pops the lowest-indexed pending propagator, not the least-recently-queued one. This
  preserves `PROPAGATORS`' documented ordering — AC3 and nogoods sit last precisely so the cheap
  bounds propagators narrow domains first, both placements backed by measured regressions when moved
  earlier — and collapses repeated wakes of an expensive pass into a single run carrying the union of
  everything that woke it.
- `FixpointConsistency` drives its constraints from a `ConstraintQueue` that re-propagates a
  constraint only when one of its own variables has been narrowed since it last ran, which is
  `relevant()`'s dirty-tracking argument extended from the call's entry point to iterations within
  the call.
- Because that queue leaves the type at a fixpoint, `FixpointConsistency` declares
  `ConstraintConsistency#convergesInternally()` and is not re-woken for its own changes. `AC3` does
  the same via its arc queue; `NogoodFixpointConsistency` iterates a fixed subset and does not.

`AC3` also stopped copying every domain on calls that revise nothing (commit `9f9b2d8`), both
because it was per-node waste and because the worklist needs a truthful reference-equality signal.

## Consequences

The fixpoint is unchanged. Verified directly rather than argued: post-propagation domains were dumped
per variable for ten corpus instances under both implementations and compared byte-for-byte,
including the two whose node counts diverged.

What does change is *which* propagator first reports a wipeout, and therefore which nogood is
learned — the order-dependence `PROPAGATORS`' own ordering comment already records. Search can
diverge from that in either direction: `Crossword-lex-vg-5-6` and `driverlogw-09` both shifted node
counts materially, and a four-seed sweep showed both to be luck rather than trend.

Measured, fixed seed, interleaved A/B, three reps. Where node counts are identical the comparison is
pure per-node cost; where the instance exhausts its budget, nodes measure throughput:

| Instance | Signal | Before | After | |
|---|---|---|---|---|
| Bibd-sum-06-050-25-03-10 | ms @ 2,322 nodes | 1,582 | 744 | −53% |
| MagicSquare-6-sum | ms @ 2,413 nodes | 2,500 | 1,737 | −31% |
| qcp-15-120-00_X2 | ms @ 7,932 nodes | 2,066 | 1,500 | −27% |
| Steiner3-08 | nodes @ 30s | 381,390 | 431,297 | +13% |
| LowAutocorrelation-015 | nodes @ 30s | 554,591 | 598,816 | +8% |

A whole-corpus sweep (85 instances, 20s budget, the runner's own fixed seed) against the
pre-session baseline: 65 solved, up from 64 -- `Sat-flat200-00-clause` newly solved -- with zero
solved instances lost, zero changed answers, and zero `SolutionChecker` mismatches across the
validated solutions.

The worklist and the per-constraint queue were **measured together and are not separately
attributed**. Given a 2-to-5-entry propagator list, skipping propagator invocations cannot be the
main effect; the removed per-round `domainSum`/diff scans and the per-constraint queue are the
plausible sources, but that split was not measured and should not be asserted.

## Rejected alternatives

- **A FIFO worklist.** Discards `PROPAGATORS`' ordering, which two separate measured decisions
  depend on. Lowest-index-first costs a scan of the (short) filtered list per pop and keeps them.
- **Per-constraint granularity at the outer level**, the full Choco design. `ConstraintConsistency`,
  not `Constraint`, is this codebase's propagation unit, and `AC3`/`NogoodFixpointConsistency` are
  not per-constraint at all. Per-constraint queuing was instead added *inside* `FixpointConsistency`,
  which is where it pays without disturbing the abstraction.
- **Waking a propagator for its own changes.** The first implementation did, and systematically
  regressed `driverlogw-09` by ~47% across all four seeds tried. The reason is visible in the table
  above: that instance has three applicable propagators, so the worklist had nothing to skip and only
  added invocation overhead to a round-robin already near-optimal for that shape, re-entering the
  conflict-table pass once per wave of narrowing. `convergesInternally` plus the per-constraint
  `ConstraintQueue` removed the regression by letting one invocation converge instead.
- **Deriving changed variables from a field recorded by `withDomains`** rather than diffing the
  domain map. Cheaper, but a stale or wrong value would silently weaken the fixpoint, and the diff
  only runs when a propagator genuinely changed something — by which point it is doing real work
  anyway. Reconsider only with evidence the diff is hot.
