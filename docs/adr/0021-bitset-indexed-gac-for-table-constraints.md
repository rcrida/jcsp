# 0021. Bitset-indexed GAC for NaryTuplesConstraint

**Status**: Implemented

## Context

`NaryTuplesConstraint.propagate()` computed GAC via a plain stream scan: filter `tuples` down to
those consistent with every variable's current domain (`O(tuples × variables)`, each check a
stream-dispatched `Domain#contains` hash lookup), then a second pass per variable collecting which
values remain supported. Fine for small tables, but for `Steiner3-08.xml.lzma` (36 constraints,
80,640 tuples/6 variables each) this dominates real per-node cost — confirmed via JFR after two
unrelated hot-path bugs were fixed (see `project_jcsp_identityhashmap_and_withnogoods_fastpath` in
project memory, which is what exposed this as the *legitimate* remaining bottleneck rather than a
bug) and reconfirmed on `driverlogw-09.xml.lzma` (17,447 mostly-table constraints; ADR-0019's
per-object dirty tracking fixed a different, driver-level issue but left this per-call cost
unchanged — still ~5,870 constraint checks/node, essentially the same as ADR-0019's original
measurement).

This was scoped as "STR2-style indexed GAC" in earlier project memory
(`project_jcsp_str2_table_constraint_future_work`), but reasoning through classic STR2 (Lecoutre
2011) before implementing found it doesn't fit this codebase's architecture: STR2's speed comes
from a *mutable*, backtracking-coupled "current tuple list" that gets compacted in place during
search and restored via an explicit undo stack on backtrack. Two properties of jcsp make that
shape unsound here:

- Every propagator is a pure function, `propagate(domains) -> Optional<domains>`, with no notion of
  "this call follows that one in a DFS chain" — it's invoked from arbitrary domain snapshots
  (one-time preprocessing, a B&B node, a local-search repair move), not necessarily a strict
  backtracking trajectory a mutable undo stack could hook into.
- `Constraint` objects are shared and invoked **concurrently** across threads: `RaceLocalSolver`
  runs `MinConflictsSolver` and `TabuSearchSolver` in parallel virtual threads over the same CSP,
  meaning the same `NaryTuplesConstraint` instance's `propagate()` can be called from multiple
  threads at once. Any mutable per-instance state (STR2's last-support pointers, its compacted
  tuple array) would be a genuine thread-safety bug, not just an engineering wrinkle.

## Decision

Build an *immutable*, lazily-computed inverted index instead: for each variable and each value that
appears in some tuple, a `BitSet` over tuple positions (bit `j` set iff tuple `j` assigns that
value to that variable). `propagate()` then computes, per variable, the union (`BitSet#or`) of its
currently-live values' support bitsets, and intersects (`BitSet#and`) those unions across every
variable to get the live-tuple set — empty means infeasible, checked incrementally so an early
domain wipeout skips building the rest. Pruning checks `BitSet#intersects` per (variable, value)
pair against the live set.

This is **not an asymptotic improvement** — still `O(tuples/64 × variables)` per call, not
sub-linear the way STR2's genuine incrementality is — it eliminates per-tuple
stream/lambda/boxing/hash-lookup overhead in favour of word-parallel bitwise ops, the same category
of win as the "AC3 frozen-domains" and "MAC withDomain fast-path" fixes already landed in this
codebase (see project memory). The index is built once per constraint instance (a pure function of
the immutable `tuples` field, cached via `AtomicReference#compareAndSet` — a benign race if two
threads build it concurrently on first use, since both computations are equivalent and idempotent)
and never mutated afterward, so it's safe to read concurrently with no locking beyond that initial
cache population — sidestepping both objections to literal STR2 above without needing any
backtracking-aware undo mechanism or thread-confined state.

`explainInfeasible` needed no changes: it already derives its nogood purely from current domains
via `RangeNogoodConstraint#fromCurrentBounds`/`Propagatable#allSingletonReason`, with no dependency
on `propagate`'s internal representation.

## Rejected alternatives

- **Literal STR2** (mutable current-tuple-list + backtracking-coupled undo). Rejected for the two
  architectural reasons in Context: no DFS-chain assumption to hook an undo into, and constraint
  objects are shared across concurrently-executing threads.
- **A synchronized/locked mutable index** (STR2's shape, but guarded by a lock for thread safety).
  Rejected: the whole point of avoiding the naive scan is speed — serializing every `propagate()`
  call on a large table constraint behind a lock would reintroduce exactly the kind of contention
  bottleneck this change exists to remove, and `RaceLocalSolver`'s two solvers are specifically
  racing for wall-clock advantage, where added lock contention directly undermines the mechanism
  (see ADR-0003).
- **Extending the same treatment to `NaryStarredTuplesConstraint`/`NaryConflictTuplesConstraint` in
  the same change.** `NaryConflictTuplesConstraint` already has a good, different algorithm
  (pigeonhole counting over the complement — see its own Javadoc); no evidence it needs this.
  `NaryStarredTuplesConstraint` is a plausible future candidate (a wildcard cell would need to set
  bits in *every* value's bucket for that column) but no corpus instance was found where it's the
  bottleneck — left as unbuilt future work rather than speculative scope.

## Consequences

- `NaryTuplesConstraint` gained a new `private final AtomicReference<...> supportIndex` field,
  excluded from `@EqualsAndHashCode` (a derived cache, not part of constraint identity — `tuples`
  itself already determines that) and invisible to the `@SuperBuilder`-generated builder (a field
  with a direct initializer and no `@Builder.Default` is builder-invisible by Lombok's own
  convention, which is exactly the "every instance starts with its own fresh empty cache" behaviour
  wanted here).
- Memory: bounded and one-time, not per-call. Steiner3-08's shape (~48 total (variable, value)
  bitsets per constraint × 80,640 bits ≈ 480KB/constraint × 36 constraints ≈ 17MB) is representative
  of the scale this is meant for; a small table's index is correspondingly tiny.
- Verified via the full existing test suite (3,112 tests, zero failures, 100% instruction/branch
  coverage with no new tests needed — the existing `NaryTuplesConstraint` suite already exercised
  every code path this rewrite touches) plus the full bundled XCSP3 competition corpus
  (`Xcsp3CompetitionRunner`) with zero regressions and zero new `SolutionChecker` mismatches.
- On `Steiner3-08.xml.lzma`: nodes explored at a comparable-or-larger budget went from the
  documented baseline of 338 (post-ADR-0019, 20s budget) to 320,005 (30s budget) — roughly three
  orders of magnitude more search throughput. Still doesn't reach a solution within that budget
  (this fix targets propagation *cost*, not search/branching quality — Steiner3-08's remaining
  difficulty may be genuinely combinatorial), but the propagation bottleneck itself is resolved.
- On `driverlogw-09.xml.lzma`: a much smaller improvement (nodesExplored ~3,200 → ~4,381 at a
  comparable budget), as expected — its 17,447 constraints are dominated by
  `NaryConflictTuplesConstraint` (untouched by this change, 15,349 of them), with only 2,098
  `NaryTuplesConstraint` benefiting here.

## Future work

- **`NaryStarredTuplesConstraint`**, if a concrete corpus or user-reported instance is found where
  its own `O(tuples × variables)` scan (the same shape, plus a `STAR` special case) is the
  bottleneck the way `NaryTuplesConstraint`'s was here.
- **Genuine incremental GAC** (STR2-equivalent sub-linear amortized cost across search nodes) would
  need a larger architectural change — giving propagators real, thread-confined, snapshot/restorable
  state tied to the search tree, likely touching `FixpointPropagation`, `SolverListener`, and
  possibly a "propagator instance per search thread" concept. Not attempted here: the bitset
  approach's measured three-orders-of-magnitude throughput gain on the motivating instance suggests
  the per-call overhead, not the lack of true incrementality, was the dominant cost — revisit only if
  profiling on a future large-table instance shows the *remaining* per-call bitset cost itself (not
  overhead around it) is the bottleneck.
