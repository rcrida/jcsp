# 0018. Disjunctive edge-finding propagator

**Status**: Accepted

## Context

Investigating why `Taillard-js-015-15-0.xml.lzma` (a 15×15 Taillard job-shop instance, one of the
classic hardest CP scheduling benchmarks) ran slowly traced back to a real gap: XCSP3's `noOverlap`
1D form, and `Prob061JobShopSchedulingTest`'s own same-machine mutual exclusion, both modelled
"these tasks can't overlap on one resource" via `cumulativeConstraint(limit=1)`.
`CumulativeConstraint#propagate` only implements timetabling (compulsory-part overlap detection),
which contributes nothing until tasks' domains have already narrowed enough for compulsory parts to
exist — with Taillard-js's wide initial start-time domains (`0..1330`), almost nothing overlaps at
the top of the search tree, so timetabling barely prunes there. A separate investigation the same
session confirmed `BranchAndBoundSolver`'s LP-relaxation fast path contributes nothing for this
instance either (zero `Sum`/`Linear`-type constraint rows exist to build an LP from), so
propagation strength was the only lever available. Real CP solvers rely on **edge-finding**
(Carlier & Pinson 1989; Baptiste, Le Pape & Nuijten 2001; Vilim 2007) for exactly this class of
problem — reasoning about groups of tasks' combined time windows rather than individual compulsory
parts, so it can tighten bounds even when no task has a compulsory part at all.

## Decision

Added `DisjunctiveConstraint` (`constraints.nary` package) — a new class, not a change to
`CumulativeConstraint`. Edge-finding's classical formulation is specific to a **unary** resource
(each task either fully occupies the resource or not); the general-capacity "cumulative
edge-finding" is a separate, substantially more complex algorithm not needed here. This matches how
other CP solvers draw the line (Gecode, Chuffed, and Choco all expose a dedicated
`disjunctive`/`unary` propagator distinct from their general `cumulative` one). `CumulativeConstraint`
is untouched — still used directly for genuine multi-capacity resources (e.g.
`SprintSchedulingTest`).

`DisjunctiveConstraint` implements the two standard edge-finding rules over "task intervals" (sets
of the form `{k : est_k ≥ est_i, lct_k ≤ lct_j}` for some pair of tasks `i, j` — a well-established
sufficiency result: only these `O(n²)` sets can ever produce the tightest bound, not all `2ⁿ`
subsets):

- **Overload rule**: `est(Θ) + p(Θ) > lct(Θ)` for any task-interval `Θ` ⟹ infeasible.
- **Edge-finding rule**: for `Θ` and task `j ∉ Θ` with `est_j ≥ est(Θ)`, if
  `est(Θ) + p(Θ) + p_j > lct(Θ)`, then `j` cannot finish by `lct(Θ)` alongside every task of `Θ`,
  forcing `est_j ← max(est_j, est(Θ) + p(Θ))`.

Implemented as a direct `O(n³)`-worst-case task-interval enumeration (outer loop over an `est`
threshold, inner loop growing `Θ` by increasing `lct`, innermost `O(n)` scan applying the rule to
each candidate `j`) — see Rejected Alternatives for why this is not Vilim's fully-optimized
`O(n log n)` Θ-Λ-tree. A single private `edgeFind` method (in `DisjunctiveConstraint`) implements
both rules together (an overloaded task-interval discovered while growing `Θ` is detected as part
of the same sweep that derives `est` tightenings) and is called twice: once directly (tightening
`est`), once on a time-reversed instance (`est'_i = -lct_i`, `lct'_i = -est_i`) to derive the mirror
`lst`-tightening bound, avoiding a second, separately-derived algorithm.

**Discrete (`IntRangeDomain`) tasks only — no continuous overload.** `CumulativeConstraint` supports
both `IntRangeDomain` and `IntervalDomain` start variables via two `of(...)` overloads,
disambiguated only because their trailing `int limit`/`double limit` parameter isn't generic-erased.
`DisjunctiveConstraint` has no `limit`/`resources` parameter at all (fixed at 1) — a same-shaped
`Integer`/`Double` pair of `of(...)` overloads would erase to identical signatures (JLS 8.4.2), the
exact pitfall `linearBooleanConstraint`/`diffnVariableConstraint` were named apart to avoid. No real
use case in this codebase needs continuous disjunctive scheduling (XCSP3-core is integer-only;
`Taillard-js`; `Prob061`), so this was scoped out rather than solved with a new name up front. Not
added to `ConstraintSatisfactionProblem`'s `CONTINUOUS_COMPATIBLE_CONSTRAINTS` whitelist (see
[ADR-0006](0006-whitelist-based-domain-constraint-compatibility.md)) for the same reason.

`Xcsp3CallbackHandler#buildCtrNoOverlap`'s 1D overload now routes through `DisjunctiveConstraint.of`
instead of `CumulativeConstraint.of(..., 1)`; `Prob061JobShopSchedulingTest`'s two same-machine
constraints were switched the same way.

## Rejected alternatives

- **Branching `limit == 1` inside `CumulativeConstraint#propagate` instead of a new class.**
  Rejected: edge-finding's task-interval reasoning has nothing in common with timetabling's
  compulsory-part sweep beyond both computing `est`/`lst` bounds from the same domains — folding
  both algorithms into one class via a capacity-based branch would make `CumulativeConstraint`
  harder to read for no shared logic, and permanently pay edge-finding's `O(n³)` cost even for
  genuine multi-capacity `cumulative` calls where it doesn't apply at all.
- **Vilim's `O(n log n)` Θ-Λ-tree algorithm.** Deferred, not rejected outright — the fully-optimized
  version needs a nontrivial augmented balanced-tree data structure. Real target instance sizes in
  this codebase (job-shop machines: tens of tasks, not thousands — Taillard-js is 15 tasks/machine)
  don't need it, and this codebase consistently favors a clear, well-documented algorithm over
  squeezing out asymptotic headroom real instances don't demand (`CumulativeConstraint`'s own
  timetabling is a plain sweep, not the more advanced synchronized-sweep variant either). Revisit if
  profiling on a larger real instance ever shows the `O(n³)` enumeration itself as the bottleneck.
- **Also supporting continuous (`IntervalDomain`) tasks now.** Deferred — no real use case exists in
  this codebase, and it would need its own naming decision (e.g. `continuousDisjunctiveConstraint`)
  to avoid the erasure collision described above; not worth resolving speculatively.
- **A `backward.infeasible()` check, mirroring the forward pass's, in both `propagate` and
  `explainInfeasible`.** Removed during implementation: the overload rule is symmetric under the
  same time-reversal used to get from forward to backward (`minEst(Θ) + p(Θ) > maxLct(Θ)` names the
  exact same real quantities either way), so once the forward pass has confirmed no task-interval
  is overloaded, the backward pass's own overload check can never trigger either — a real dead
  branch, not just a hard-to-reach one. Confirmed via JaCoCo (the branch never covered by any
  constructible test) before removing it, not assumed.

## Consequences

`noOverlap`/`Prob061` (and any future direct `disjunctiveConstraint` caller) now get real
edge-finding instead of a compulsory-part-only fallback. A distinct failure mode exists beyond
"some task-interval directly overloaded": the forward and backward passes can independently derive
sound bounds that only conflict once combined (`propagate`/`explainInfeasible` handle this as a
separate case, citing every task in the constraint rather than a narrower culprit subset — verified
to actually occur via brute-force search over small random instances, not merely a defensive
branch). A future continuous-domain extension needs a distinctly-named factory, not an overload, per
the scope decision above.
