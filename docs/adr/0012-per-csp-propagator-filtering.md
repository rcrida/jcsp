# 0012. Per-CSP propagator filtering for the fixpoint loop

**Status**: Accepted

## Context

`FixpointPropagation.PROPAGATORS` ran a fixed, unconditional list of all ~40 registered propagators
(one per propagatable constraint type, plus AC3 and nogood checking) every round, for every CSP, at
every search node — regardless of whether that CSP actually has any constraint of a given
propagator's type. Each individual propagator already short-circuited internally on a miss (a
cached empty-list check), but the fixpoint loop still had to call into every entry to discover
that — on the hot path invoked at every search node, every `SetBranchingSolver` branch step, and
once for whole-CSP preprocessing.

## Decision

`FixpointPropagation` becomes an instantiable `@Value @Builder` type carrying an explicit
`propagators` list, with `applyFixpoint`/`applyFixpointWithReason` converted from `static` methods
to instance methods over that list. A new nested `FixpointPropagation.Factory` interface (mirroring
`Solver.Factory`'s own `INSTANCE` shape) computes, once per solve inside
`Solver.Factory.createSolver`, a filtered instance via `forProblem(csp, nogoodLearningEnabled)`:

- A `FixpointConsistency.of(XxxConstraint.class)` entry is included only if `csp.getConstraints()`
  or `csp.getAllBinaryConstraints()` contains an instance of `XxxConstraint` — the latter check
  catches constraint types that only appear via a `BinaryDecomposable`'s decomposition, including
  ones promoted into cutset/tree sub-CSPs that the top-level CSP never had directly.
- `AC3.INSTANCE` is included only if the CSP has any binary or binary-decomposable constraint.
- `NogoodFixpointConsistency.INSTANCE` is included whenever nogood learning is enabled, or whenever
  the CSP already carries nogoods of its own (pre-seeded via the builder), regardless of the flag.

The filtered instance is computed once from the *top-level* CSP and shared by reference into
`PropagationFixpointSolver`, the per-solve `Inference` (`Solver.Factory#propagationInference`), and
`SetBranchingSolver` for the whole solve — safe because every sub-piece (independent subproblems,
cutset/tree sub-CSPs via domain narrowing, `SetBranchingSolver`'s branch-narrowed CSPs) has a
constraint-type set that's a subset of (or, via the `getAllBinaryConstraints()` check above, safely
bounded by) the top-level CSP's.

`FixpointPropagation.PROPAGATORS` remains the full, unfiltered catalog — the thing filtered *from*,
not something removed — and `FixpointPropagation.FULL` (backed by it) remains available as the
always-everything instance, used by `Solver.Factory#FULL_PROPAGATION_INFERENCE` (an unfiltered
fallback for direct/manual/test use that bypasses `createSolver` entirely) and as the default for
`PropagationFixpointSolver#fixpointPropagation`/`SetBranchingSolver#fixpointPropagation` when
neither is threaded a filtered instance.

## Rejected alternatives

- **Keep `FixpointPropagation` fully static**, and instead have `applyFixpoint`/`applyFixpointWithReason`
  take the propagator list as an extra parameter at each of the three call sites. Rejected: this
  pushes the "which list" decision onto every caller instead of encapsulating it in the type itself,
  and doesn't give `PropagationFixpointSolver`/`SetBranchingSolver` a natural field to default and
  override.
- **A plain `public static forProblem(...)` factory method** (mirroring `NogoodStore.forProblem`)
  instead of a nested `Factory` interface with a static `INSTANCE`. Considered for consistency with
  that existing precedent, but the nested-interface-with-`INSTANCE` shape was chosen deliberately to
  mirror `Solver.Factory.INSTANCE`'s own established pattern in this codebase, since both play the
  same "constructs the thing `Solver.Factory#createSolver` needs, given a CSP" role.
- **Filter every sub-CSP separately** (recomputing `forProblem` inside `IndependentSubproblemSolver`'s
  `innerFactory`, or per cutset/tree conditioning step) instead of once per solve. Rejected as
  unnecessary complexity: since a sub-CSP's constraint types are always a subset of (or safely
  bounded relative to) the top-level CSP's, a single top-level filter can only be wasteful for a
  particular sub-piece, never lossy — recomputing per sub-CSP would only trim a few more no-op
  propagators at the cost of doing the filtering work repeatedly.
- **Gate `NogoodFixpointConsistency` purely on `nogoodLearningEnabled`**, ignoring whether the CSP
  already carries nogoods. Reverted after review: `SolverConfig#isNogoodLearningEnabled()`'s
  documented contract is "disables CDCL" (no explanation computation, no accumulation of
  newly-learned nogoods), not "ignore nogoods already on the problem" — a caller pre-seeding
  nogoods via the builder and disabling learning for determinism/benchmarking must still get them
  propagated.
- **Cast `((FixpointConsistency) propagator)` unconditionally** in the one place `isApplicable`
  doesn't reference the two singletons by identity. Reverted after review in favor of
  `!(propagator instanceof FixpointConsistency fc) || fc.appliesTo(csp)`: `PROPAGATORS` is a public
  list documented as safe to append to, so a future non-`FixpointConsistency` entry must fail open
  (assumed applicable, harmless if actually irrelevant) rather than throw `ClassCastException` on
  every solve.

## Consequences

- Adding a new propagator stays a one-line `FixpointConsistency.of(MyConstraint.class)` entry
  appended to `PROPAGATORS` — automatically picked up by both `FULL` and `forProblem`'s filtering,
  with no second list to update.
- `FixpointPropagation.applyFixpoint`/`applyFixpointWithReason` moving from `static` to instance
  methods is a source-breaking change for any external caller of this `public` class (published to
  Maven Central) — mitigated by `FULL` remaining available as a drop-in instance to call the same
  methods on.
- Measured impact: isolated at the `applyFixpoint` call level (same CSP, same JVM, filtered vs.
  `FULL`), filtering gives a genuine, reproducible ~15–25% per-call speedup. In full end-to-end
  solves at small/medium CSP sizes, this saving is within normal run-to-run noise — the fixpoint
  loop is only one part of per-search-node cost (`Assignment` construction, immutable CSP/domain-map
  rebuilding, variable/value selection), so the win is real but doesn't reliably show up in
  wall-clock time at that scale; it would matter more for solves dominated by very large numbers of
  search nodes.
- `SolverConfig#nogoodLearningEnabled` (ADR-0005) now has a second effect beyond choosing the
  `Inference` wrapper: it also decides, together with whether the CSP already carries nogoods,
  whether `NogoodFixpointConsistency` is filtered into the propagator list at all — see ADR-0005's
  own Consequences for the pointer.
