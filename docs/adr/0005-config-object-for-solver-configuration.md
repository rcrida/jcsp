# 0005. Config-object pattern for solver configuration

**Status**: Accepted

## Context

`Solver.Factory.createSolver` started with a single no-arg overload and grew one additional overload
per new configuration knob (node/time limits, then nogood-learning toggle, then statistics). Each
new knob meant another `createSolver` overload combination, an approach that doesn't scale as more
knobs get added — the overload set grows combinatorially, not linearly, once knobs can be combined
independently.

## Decision

Bundle every `createSolver` configuration knob (`limits`, `nogoodLearningEnabled`, `statistics`)
behind one `@Value @Builder` type, `SolverConfig`, with `@Builder.Default`s for each knob.
`createSolver(csp, SolverConfig)` and `createSolver(csp, objective, SolverConfig)` are the two true
abstract methods on `Solver.Factory`; the no-arg and objective-only overloads default to
`SolverConfig.builder().build()`.

This replaced the prior `createSolver(csp, SolverLimits)`/`createSolver(csp, objective,
SolverLimits)` signatures outright rather than deprecating them — a deliberate breaking change given
the accumulating-overloads problem it fixes; deprecating and keeping both would have left the
combinatorial-overload problem half-solved.

`nogoodLearningEnabled` (a plain `boolean`) affects both chains uniformly:
`Solver.Factory`'s shared `nogoodLearningInference(SolverConfig)` helper picks between
`FULL_PROPAGATION_INFERENCE` (`true`) and `Inference.withoutReasonTracking(FULL_PROPAGATION_INFERENCE)`
(`false`, disabling CDCL entirely — no explanation computation, no accumulation, for problem shapes
where learned nogoods rarely get reused), and both `DomWdegLubySearch` and `BranchAndBoundSolver`
are handed whichever `Inference` that helper returns.

## Rejected alternatives

- **Continuing to add one `createSolver` overload per knob.** Rejected as the status quo being
  fixed — see Context above.
- **Deprecating the old `SolverLimits`-based overloads instead of removing them.** Rejected: keeping
  both forms would still let two "with limits, without the new knobs" and "with the new knobs"
  call styles coexist indefinitely, undermining the point of consolidating configuration into one
  place. See `feedback_config_object_over_growing_overloads` in project memory — this is now the
  standing precedent for any future factory method whose parameter list is growing; ask before
  choosing breaking-replace vs. deprecate-and-keep on a case-by-case basis, but default to the
  former when the overload growth itself is the problem being solved.

## Consequences

- A future configuration knob is a new `SolverConfig` field with a `@Builder.Default`, not a new
  `createSolver` overload.
- `nogoodLearningEnabled` was originally a `conflictExplainer` field, simplified to a plain
  `boolean` once `ConflictExplainer` was merged into `Inference` (ADR-0002) — the config object's
  shape itself had to be revisited once that separate decision landed, illustrating that a config
  object doesn't freeze the *meaning* of its knobs, only their bundling.
