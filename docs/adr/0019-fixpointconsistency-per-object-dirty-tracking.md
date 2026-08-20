# 0019. Per-object dirty tracking in FixpointConsistency

**Status**: Accepted

## Context

Investigating why `driverlogw-09.xml.lzma` (a planning-domain instance compiled to XCSP3) ran
slowly found a real architectural gap, not a per-constraint algorithm problem. The instance has
only 650 variables but 17,447 constraints — 15,349 `NaryConflictTuplesConstraint` + 2,098
`NaryTuplesConstraint`, mostly small binary tables from XCSP3's `<group>`-templated `<extension>`
blocks. Measured directly: 30s of search explored only 1,021 nodes (~34 nodes/sec) but ran 6.33
million constraint checks — ~6,200 per node.

The cause traced to `FixpointConsistency`, the class every `FixpointPropagation.PROPAGATORS`
entry (besides `AC3`/`NogoodFixpointConsistency`) is built from. Its `apply`/`applyWithReason`
iterated the *entire* filtered list of constraint objects of a type every fixpoint round, with no
per-object relevance check — `ConstraintConsistency`'s own Javadoc documented this as deliberate:
"passes whose cost scales with a fixed, small constraint count (every `FixpointConsistency`
instance, AC3) have no need for the [`changedSinceLastRun`] hint." That "fixed, small constraint
count" assumption is exactly what XCSP3's `<group>` templating breaks. The 2-arg
`apply(csp, changedSinceLastRun)` wasn't even overridden by `FixpointConsistency` at all, silently
falling through to the interface default (discarding the hint entirely) — meaning the one-time
preprocessing pass (`applyFixpoint`, used by `PropagationFixpointSolver`) got zero benefit from
the hint for any `FixpointConsistency`-backed propagator.

`NogoodFixpointConsistency` already solves this exact problem for the other case where one
propagator type can have unboundedly many instances (learned nogoods): `relevant()` filters via
`Collections.disjoint(nogood.getVariables(), changed)`, using a `Variable -> Set<NogoodConstraint>`
index (`NogoodStore#byVariable`) when available for an O(changed.size()) lookup instead of an
O(nogoods.size()) scan.

## Decision

Generalize `NogoodFixpointConsistency`'s `relevant()`/index pattern into `FixpointConsistency`
itself. Unlike the nogood case, the index is built **unconditionally**, not optionally: a
constraint type's instance set is fixed at CSP-build time and never mutates mid-solve (unlike
nogoods, which continuously grow/get evicted — the reason two earlier attempts at *rebuilding* a
nogood index from scratch on every learn event were reverted as net losses, per
`NogoodFixpointConsistency`'s own Javadoc). `FixpointConsistency` builds a
`Map<Variable<?>, List<Propagatable>>` index once, lazily, cached by the exact same mechanism its
existing type-filtered list already uses (`csp.computeAuxiliaryCacheIfAbsent`, invalidated only
when `csp.getConstraints()` changes reference) — no scan-fallback path needed. A plain
`Collections.disjoint` scan wouldn't have solved the measured problem anyway: it still costs
O(constraints.size()) just to decide the filtered subset, which for 17,447 objects is no cheaper
than calling `propagate()` on all of them directly. The index turns filtering into
O(changed.size()), which is the actual win.

The 1-arg `apply(csp)` becomes a thin delegate to `apply(csp, null)`; the missing 2-arg
`apply(csp, changedSinceLastRun)` override was added; `applyWithReason(csp, changedSinceLastRun)`
now filters through the same `relevant()` helper instead of iterating the unfiltered list. No
individual constraint class changed — this fix operates entirely at the fixpoint-driver level,
which is what makes it general: every `FixpointConsistency`-backed propagator type benefits
automatically, not just table constraints.

**Soundness**: identical to `NogoodFixpointConsistency`'s own established argument — a
`Propagatable#propagate` result depends only on the current domains of that constraint's own
`getVariables()`, so a constraint object none of whose variables changed since it was last checked
is provably unable to produce a different result now. This relies on a precondition that was
already documented (not newly introduced) in `FixpointPropagation#applyFixpoint`'s own Javadoc:
a search-node call's `initialSeed` is valid specifically because "this call's input is exactly the
parent's already-converged CSP — nothing else could have changed." `PropagationFixpointSolver`
(one-time preprocessing) and `SetBranchingSolver` (per branch step) both always call `applyFixpoint`
with a `null` seed (a genuine full scan), establishing that "already-converged" baseline before any
narrower-seeded call ever happens.

## Rejected alternatives

- **A plain `Collections.disjoint` scan without an index**, mirroring `NogoodFixpointConsistency`'s
  own fallback path. Rejected: still O(constraints.size()) just to compute the filtered subset,
  which doesn't actually solve the measured problem for a type with thousands of instances.
- **Fixing this per-constraint-class instead** (e.g. residual-support/Compact-Table-style caching
  inside `NaryTuplesConstraint`'s own `propagate()`). Considered as the original scope for this
  investigation, but rejected as narrower once the actual bottleneck was traced to the fixpoint
  driver, not any single constraint's own algorithm: a table-internal fix would only help table
  constraints, not every other `FixpointConsistency`-backed propagator type that could similarly
  end up with many instances from XCSP3 `<group>` templating.

## Consequences

- Every propagator type registered via `FixpointConsistency.of(...)` now gets per-object dirty
  tracking automatically — no change needed at each constraint class.
- The one-time preprocessing pass (`applyFixpoint`) also benefits now, not just the per-node hot
  path, since the previously-missing 2-arg `apply` override was added.
- Surfaced a genuine latent test bug while implementing this: `FullPropagationInferenceTest`'s
  `applyWithReason_fixpointFindsReason_returnsReasonInsteadOfAssignment` constructed a CSP with an
  already-infeasible constraint entirely disconnected from the seeded assignment's own propagation
  chain — a scenario that violates `applyFixpoint`'s own documented "already-converged" precondition
  and could only ever "pass" because `FixpointConsistency`'s previous unconditional full scan was
  accidentally more thorough than its documented contract promised. Fixed by redesigning the test so
  the failing constraint's variables are a genuine, traceable consequence of the seeded assignment
  (verified to produce identical behavior under both the old and new `FixpointConsistency` code,
  confirming the redesign is robust, not an artifact of this change) — see the test's own updated
  comment for the corrected scenario.
- `ConstraintConsistency`'s own Javadoc on `apply(csp, changedSinceLastRun)` was corrected: it no
  longer claims every `FixpointConsistency` instance has "no need for the hint."
