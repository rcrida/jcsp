# 0002. Nogood learning as first-class propagatable constraints

**Status**: Accepted

## Context

Backtracking search benefits from CDCL-style nogood learning: when a branch fails, recording *why*
(a sound, ideally minimal combination of variable-value pairs that's jointly infeasible) lets later
search avoid rediscovering the same failure. The natural first design (used until 2026-07-05) was
to store a learned nogood as a plain variable-value map and check it against the search's explicit
decision map at each node.

That design has a real gap: a variable can be forced to a singleton value purely by *another*
constraint's propagation, without ever being explicitly branched on by search. A nogood citing such
a variable is permanently unmatchable against the explicit decision map, no matter how sound the
citation is — the map comparison only ever sees variables search itself chose.

## Decision

Model a learned nogood as an actual `Constraint` (`NogoodConstraint extends Constraint,
Propagatable`) that joins the same propagation fixpoint as every other constraint, rather than as a
side-channel map compared against the explicit assignment. `propagate()` reasons from current
domain state directly, so it catches the "forced, never branched" case the map comparison couldn't.

`NogoodConstraint` is an interface, not a single class, so different derivation strategies can
produce different nogood *shapes* without `NogoodStore`/`ConstraintSatisfactionProblem` (which only
depend on the interface) needing to change: `GroundNogoodConstraint` (one forbidden value per
variable), `RangeNogoodConstraint` (a forbidden numeric range per variable, for `BoundedDomain`/gapless
`DiscreteDomain`), and `SetBoundsNogoodConstraint` (a forbidden `SetBoundedDomain` region per
variable).

`ConstraintSatisfactionProblem` carries `nogoods` as a field separate from its structural
`constraints`/`ConstraintGraph` (excluded from `equals`/`hashCode` — learned nogoods are accumulated
search knowledge, not part of the problem's identity), and `getConstraints()` returns a precomputed
flat union. `NogoodStore` accumulates nogoods during search (capped at `20 * variableCount`, floored
at 50, evicting largest-arity first) and both `DomWdegLubySearch` and `BranchAndBoundSolver` fold
`nogoodStore.apply(csp)` into the CSP checked/propagated against at each candidate.

Conflict *explanation* (deriving the reason for a wipeout) evolved on 2026-07-18: it used to live
behind a separate `ConflictExplainer` interface whose sole production implementation re-derived a
nogood via a second, from-scratch traversal (re-running MAC and the whole propagator fixpoint,
unseeded) *after* `Inference.apply` had already found the same failure once. Benchmarking (isolating
pure explanation cost at matched node counts) found this added 25-90% overhead across every UNSAT
scenario for zero benefit, since the only real implementation mirrored `Inference` closely enough
that no genuine pluggability was being bought. `ConflictExplainer` was deleted; `Inference#applyWithReason`
is now the sole mechanism, and every propagation layer that can explain a wipeout does so as a
byproduct of the *same* pass that detects it (each constraint/arc's `propagate`/`revise` runs exactly
once, identical cost to the feasible path, computing a reason only at the exact point a wipeout is
found).

## Rejected alternatives

- **Nogoods as a variable-value map matched against the explicit search assignment** (the design
  used before 2026-07-05). Rejected for the "forced, never branched" gap above.
- **Separate `ConflictExplainer` re-deriving a nogood after the fact** (used until 2026-07-18).
  Rejected: pure duplication of `Inference`'s own logic, confirmed via benchmark to add 25-90%
  overhead for no benefit once the one real implementation was compared against inlining explanation
  into detection.
- **Unconditional weight/nogood learning triggered from `DomWdegLubySearch`'s `isConsistent` check**
  (tried 2026-07-17, to close the pigeonhole gap where `ExactlyOneConstraint`/`AtMostOneConstraint`
  weren't registered as propagators). Reverted: regressed `CryptarithmeticTest` ~30x (unconditional
  weight+nogood) and ~4x (weight-only) in wall-clock, with the weight-only variant also making
  pigeonhole itself worse. The gap was closed instead by giving those two constraints real
  `Propagatable` implementations (see ADR-0008's related discussion of decomposition gaps) — no
  `DomWdegLubySearch` changes needed. See `project_jcsp_isconsistent_learning_gap` in project memory
  for the full trail.

## Consequences

- Every new `NogoodConstraint` implementation must be registered by concrete class (not the
  interface) in `ConstraintSatisfactionProblem`'s domain-compatibility whitelists (ADR-0006) — the
  compatibility check matches on `getClass()`, since a nogood can legitimately cite a variable with
  a `BoundedDomain`/`SetBoundedDomain` that was already snapped/narrowed before search began.
- A single `NogoodStore` is shared across Luby restarts (`DomWdegLubySearch`) and across the whole
  optimization search (`BranchAndBoundSolver`), so learned nogoods compound rather than resetting.
- A nogood-caused rejection is architecturally indistinguishable from any other constraint-caused
  rejection at `isConsistent` *in how search treats it* — no separate decision path, still folded
  into `Statistics#backtracks` like any other rejection — which is the point: nogoods are
  first-class constraints, not a bolted-on special case. This was originally read as "no separate
  nogood statistic at all," but `Statistics#nogoodRejections` (added 2026-08-20) narrows that: a
  purely observational, additive counter incremented at both of a nogood's genuine detection sites
  (`Assignment#isConsistentAmong`'s direct violation check, `FixpointPropagation#applyFixpointWithReason`'s
  `NogoodFixpointConsistency` entry) doesn't reintroduce a decision-making special case — it answers
  "how much is CDCL contributing," a different question from "should search treat nogoods
  differently," which remains no.
- `ConstraintConsistency.explainConflict` (single-CSP-argument) is now a thin wrapper kept for
  direct callers/tests; the real per-pass mechanism is `applyWithReason(csp, changedSinceLastRun)`,
  which only `FixpointConsistency`/`AC3`/`NogoodFixpointConsistency` override with a genuine single
  traversal.
