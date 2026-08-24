# 0020. Assignment-style LP relaxation for GlobalCardinalityConstraint-linked tables

**Status**: Implemented

## Context

Investigating why `PrizeCollecting-15-3-5-0.xml.lzma` (a real XCSP3 competition instance, 15-node
prize-collecting path optimization) didn't reach `OPTIMUM FOUND` within a 60s budget found two
independent weaknesses, not one. The first — 15 XCSP3-templated `<count>` constraints, one per
successor value, each an independent `CountConstraint` with only local per-value counting — was
fixed separately (consolidating them into one `GlobalCardinalityConstraint` for Régin flow-based
GAC; see the `Xcsp3CallbackHandler#flushPendingSingleValueCounts` commit). That fix alone only
modestly reduced node count (~10%), because it improves *propagation*, not the *bound*
`BranchAndBoundSolver`'s LP relaxation (ADR-0009) prunes with.

The second weakness is the LP bound itself. `LpModelBuilder` (ADR-0009) only extracts rows from
`SumBoundConstraint`/`SumVariableConstraint`/`LinearBoundConstraint`/`LinearVariableConstraint`;
this instance's objective (`maximize sum(g[])`) is entirely determined by `NaryTuplesConstraint`
lookup tables (each item's gain is a function of its chosen successor) plus the
`GlobalCardinalityConstraint` above (at most one item may claim each successor). Neither
contributes any LP row today, so the relaxation collapses to a per-variable box bound: each
`g[i]`'s `[min, max]` from its own (already GAC-propagated) domain.

A first, simpler design was considered and rejected before implementation: add a generic
"any small table constraint → LP convex-hull row" pass to `LpModelBuilder`, independent of
`GlobalCardinalityConstraint`. Reasoning it through by hand first (rather than building and
measuring) found it wouldn't help this instance at all: since each gain table is already fully
GAC-propagated before the LP is built, a `g[i]`'s box bound is *already* exactly as tight as that
table alone can make it in isolation — an independent convex-hull row over the same two variables
adds no new information the box bound didn't already capture. What a per-table row can't see is
the *joint* effect across every item at once (the GCC's "at most one item per successor"), which
requires linking every item's table into the *same* shared representation the GCC's own cardinality
bound is expressed over.

## Decision

Add a second, narrower pass (`LpModelBuilder#addAssignmentRelaxationRows`) that recognizes the
specific "assignment" shape: a `GlobalCardinalityConstraint`'s own variables, each looked up via a
small `NaryTuplesConstraint`/`NaryStarredTuplesConstraint` table into a variable the caller's
`LinearObjective` actually has a coefficient for. For each qualifying `GlobalCardinalityConstraint`:

- Represent each of its variables via one-hot indicator variables over its *current* live domain
  values (`y_{v,val} ∈ [0,1]`, `Σ_val y_{v,val} = 1`) — rebuilt fresh every `LpModelBuilder.solve`
  call, so they automatically tighten as search narrows domains, the same way box bounds already do.
- Turn the GCC's own `cardinalityRanges` into real LP rows: `Σ_v y_{v,t} ∈ [min_t, max_t]` for each
  tracked value `t`, summed across every variable in the group.
- For each qualifying table, express the looked-up objective variable as the *same* indicators'
  weighted sum (`otherVar = Σ_val y_{key,val} · lookup(val)`) rather than an independent per-table
  convex hull — this is what ties the objective to the cardinality bound in one polytope instead of
  two disconnected ones.

The combined system is a genuine transportation/assignment LP (Birkhoff–von Neumann: its extreme
points are integral), not merely a valid-but-loose relaxation — confirmed directly in
`LpModelBuilderTest#gccWithFunctionalLookupTables_assignmentRelaxationTightensBoundBeyondBox`
(bound `-11`, the true joint optimum, versus `-20` for the same tables without the GCC).

**Soundness gate** (`functionalLookup`): a table only qualifies when it forms an exact function of
the GCC variable (`key`) — every one of `key`'s current live domain values has exactly one live
tuple (no gap, no ambiguity, `key` itself never wildcarded) — *and*, independently per looked-up
variable, that variable's value is determined (not `null`/`STAR`) for *every* one of those live
tuples, not just some. The per-variable requirement is load-bearing, not merely conservative: the
linking row is an equality tying the variable to the indicators' weighted sum, so omitting even one
value's term would force that variable to `0` whenever that value is selected — silently cutting off
a genuinely feasible LP point rather than just under-approximating it. A table failing either check
is skipped entirely (falls back to the plain box bound for that variable, exactly as before) rather
than partially modeled.

**Scale guard**: a table is only considered when its live tuple count is at most
`MAX_TABLE_TUPLES_FOR_ASSIGNMENT_RELAXATION` (64) — a per-node, per-table cap on how many indicator
variables/rows one call adds. In practice the objective-relevance requirement above already excludes
the instance's own wide tables (`PrizeCollecting`'s 16-column, ~200-tuple tables linking successors
to tour positions touch no objective variable at all), but the cap is kept as an independent,
unconditional safety net rather than relying on that requirement holding for every future caller.

## Rejected alternatives

- **Generic per-table convex-hull rows, independent of any `GlobalCardinalityConstraint`.** As
  reasoned through above: sound, and real in principle (would tighten a table whose variables are
  *also* coupled by some other linear-row constraint elsewhere), but confirmed to add nothing for
  the motivating instance, since GAC already makes an isolated table's own box bound as tight as a
  standalone convex hull over the same two variables. No concrete corpus instance was found with the
  "table + separately-linear-coupled variable" shape this alternative would actually help, so
  building it now would be speculative scope rather than evidenced. Left as unbuilt future work
  rather than implemented alongside this ADR's narrower, evidenced fix.
- **Linking `key`'s own numeric value into the indicators too** (`key = Σ_val y_{v,val}·val`, mirroring
  each table's *other* variable). Unneeded: nothing in this design reads `key`'s own LP value, only
  the indicators themselves (for cardinality rows) and the looked-up *other* variables (for the
  linking rows) — adding it would only add rows/coverage burden for no behavioral benefit, and would
  have forced a `T extends Number` constraint on `GlobalCardinalityConstraint`'s own type parameter
  that the rest of the design doesn't need (a looked-up objective variable is already guaranteed
  `Number`-typed via `LinearObjective#coefficients`'s own `Variable<? extends Number>` bound; `key`'s
  domain values never need to be numeric at all).

## Consequences

- `GlobalCardinalityConstraint#cardinalityRanges`, `NaryTuplesConstraint#tuples`, and
  `NaryStarredTuplesConstraint#tuples` gained `@Getter` (previously package-private-only fields) —
  the first cross-package reads of any of these three fields, needed for `LpModelBuilder` (a
  different package) to build the relaxation.
- Applies automatically to any `BranchAndBoundSolver` search with a `LinearObjective`; the two early
  exits (`gccs.isEmpty()`, `tables.isEmpty()`) make the detection pass cheap for the overwhelming
  majority of optimization problems that have no `GlobalCardinalityConstraint` at all, and the
  per-table checks (shared-variable count, function-of-`key`, objective relevance, scale cap) make it
  a safe no-op rather than a slowdown for the rest. Verified via the full bundled XCSP3 competition
  corpus (`Xcsp3CompetitionRunner`) with zero regressions and zero new `SolutionChecker` mismatches.
- On the motivating instance itself: nodes explored for the same 60s budget dropped further (from
  ~157K with only the `CountConstraint`-consolidation fix, to ~138K with both fixes combined — about
  21.5% below the pre-fix baseline of ~175K), but the instance's ~10^47 search space still doesn't
  reach `OPTIMUM FOUND` in that budget even with both fixes. The assignment relaxation is confirmed
  correct and genuinely tight where it applies (unit-test-verified against the true combinatorial
  optimum of a small hand-built instance of the same shape); it does not, on its own, make this
  particular large real-world instance tractable within a one-minute budget.

## Future work

- **Generic per-table convex-hull rows** (the first rejected alternative above), if a concrete corpus
  or user-reported instance is ever found with a table constraint whose variables are also coupled by
  an existing `Sum*`/`Linear*` row — the shape that would actually benefit from it.
- **Deeper chains** (a `GlobalCardinalityConstraint` variable linked transitively through more than
  one table hop to an objective variable). The current detection is strictly one hop
  (`GlobalCardinalityConstraint` variable → table → objective variable); PrizeCollecting's own
  second table layer (successor → tour position, `NaryStarredTuplesConstraint`) doesn't qualify
  today since no objective variable appears in it at all, so this wasn't evidenced as worth the
  added detection complexity.
