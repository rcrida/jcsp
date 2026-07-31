# 0010. Push-listener mechanism for solve progress

**Status**: Accepted

## Context

Callers had no way to observe a solve in progress: `getSolution()`/`getSolutions()`/
`getLocalSolution()` were black boxes until they returned. The only existing progress signal was
`Statistics` (`SolverConfig.statistics`), a poll-based counter token, and `FixpointPropagationSolver`
had a debug-log-only line (`logIfDomainSumReduced`, from the commit immediately preceding this one)
showing which propagator narrowed domains within a fixpoint round — real information that never
reached a caller, only SLF4J.

Two existing precedents shaped (and one ruled out) the design:

- **`ConflictExplainer`/`NullConflictExplainer`** (deleted in `af8c341`, see ADR-0002) — a pluggable
  *strategy* interface consumed in the search control flow. It was removed because a second,
  independent computation on the hot search-node path measured 25–90% overhead. Any progress
  mechanism needed to avoid repeating that mistake.
- **`Solver#firstReducingStep`** (a sibling, unmerged branch) — a *pull/query* API: call it once,
  get the first propagator that narrowed a domain, wrapped in a `PropagationStep` record. This
  is the maintainer's own established shape for "expose an internal signal to callers," but it
  answers a different question (inspect once) than what was needed here (observe continuously
  throughout a solve).
- **The `FixpointPropagation`/`PropagationFixpointSolver` split** (the commit immediately preceding
  this one) — pulled the propagator-fixpoint algorithm out of the decorator `PropagationFixpointSolver`
  into a free-standing class specifically because it has three structurally different callers, only
  one of which has a natural instance to hang state off. That same asymmetry — a stateless algorithm
  invoked from several unrelated call sites — is why a listener has to be threaded through as an
  explicit parameter at those call sites rather than attached as a field on any one of them.

## Decision

A push/streaming listener, not a pull/query API and not a strategy interface:

- **`SolverListener`** (`io.github.rcrida.jcsp.solver.listener`), registered via `SolverConfig`,
  observes the main solver chain: composed from three grouped interfaces —
  `SearchTreeListener` (`onNodeExplored`/`onBacktrack`/`onNogoodLearned`/`onRestart` — backtracking
  mechanics, fired by both `DomWdegLubySearch` and `BranchAndBoundSolver`, both of which derive
  candidates via `Assignment#withValue`), `OptimizationListener` (`onIncumbentImproved` —
  `BranchAndBoundSolver` only), and `PropagationListener` (`onPropagatorProgress` — fired by
  `FixpointPropagation`, used by the one-time preprocessing pass, per-search-node propagation, and
  `SetBranchingSolver`'s branch re-propagation). `SolverListener` itself declares `onSolutionFound`.
  A caller only ever implements the one combined `SolverListener` type; the grouping exists for
  documentation, not as separate registration points.
- **`LocalSolverListener`**, registered via the new `LocalSolverConfig`, observes the local-search
  chain: `onSolutionFound`, `onLocalSearchStep`, and (via `extends PropagationListener`)
  `onPropagatorProgress` — `LocalSolver.Factory#PREPROCESSORS` runs a real, if non-fixpoint,
  propagation pass before repair search starts, so this is genuinely shared with the main chain.
  `LocalSolverListener` deliberately does **not** share `SearchTreeListener`/`OptimizationListener`
  with `SolverListener`: nogoods, restarts, backtracking, and incumbent-bound pruning are
  backtracking/branch-and-bound concepts that don't exist in repair-based local search.
- **Zero-cost-when-unregistered**, everywhere: every listener is defaulted to a `NONE` sentinel and
  gated with reference equality (`listener == SolverListener.NONE`), never `instanceof`/dispatch.
  `FixpointPropagation#logIfDomainSumReduced`'s existing `debugEnabled` gate (added the commit
  before this one) was extended in place to `!debugEnabled && listener == SolverListener.NONE`,
  rather than adding a second, independent check — the extra `domainSum` computation it guards is
  now paid when debug logging *or* a real listener is active, matching the cost profile debug
  logging already had.
- **`onPropagatorProgress` carries the full before/after domain maps**, not just summary doubles —
  a deliberate widening from the original "cheap primitives only" design, made after confirming
  `ConstraintSatisfactionProblem.variableDomains` is populated exclusively through Lombok's
  `@Builder`/`@Singular` machinery (no direct non-builder construction anywhere in the codebase), so
  the map handed to a listener is already immutable and the hand-off is a bare reference read, not a
  copy. The event only fires on the same already-gated "domain-sum actually reduced" branch as
  before, not on every propagator invocation.
- **`Assignment` gains a third record component, `listener` (`SolverListener`-typed)**, mirroring
  exactly how `statistics` already flows through `withValue`/`toBuilder()` as a shared token seeded
  once into the root `Assignment` and carried forward by every derived one. This is the one
  necessary exception to "listener as an explicit parameter": `Solver.Factory#FULL_PROPAGATION_INFERENCE`
  is a static singleton `Inference` with no per-solve state of its own, but its `apply`/
  `applyWithReason` already receive `Assignment` per call, so `assignment.listener()` is how a
  per-solve listener reaches propagator-progress events at that one call site. `Assignment.ofTrusted`
  (used by `LargeNeighborhoodSolver`'s hot per-combo path) was widened to a required third
  `listener` parameter for the same reason its `statistics` parameter is required, not defaulted: a
  caller deriving from an existing `Assignment` must explicitly carry the parent's token forward,
  not silently start a fresh, disconnected one.
- **`LocalSolver.Factory#createLocalSolver` gained a `LocalSolverConfig` parameter, deprecating
  (not replacing) the existing 3-arg overload.** This deliberately differs from ADR-0005's
  precedent of a breaking replace for `SolverConfig`: ADR-0005's own rejected-alternatives section
  frames that as a case-by-case call, and there the overload growth itself was the problem being
  fixed (multiple knobs, growing combinatorially). Here there is exactly one knob (`listener`)
  being added to a method that previously took none, so preserving the existing signature via
  deprecation is lower-risk with no combinatorial-overload problem to justify a breaking change.

## Rejected alternatives

- **A `ConflictExplainer`-style strategy interface.** Rejected outright — see Context; this is the
  literal mistake that regressed 25-90% and was subsequently deleted.
- **A pull/query API matching `firstReducingStep`.** Answers a different question (inspect once vs.
  observe continuously); the two are not competing designs for the same need.
- **One flat interface for both chains.** Considered, then rejected once it became clear
  `onPropagatorProgress` can *never* fire through `LocalSolverConfig`'s `PREPROCESSORS` path if that
  path isn't instrumented (it wasn't, until this change), and that `onBacktrack`/`onNogoodLearned`/
  `onRestart`/`onIncumbentImproved` are structurally inapplicable to repair-based local search — a
  shared type would have exposed several provably-dead methods depending on which config it was
  registered through.
- **`onPropagatorProgress` carrying only summary doubles, never domain maps.** The original design,
  revised after confirming the maps are already immutable and free to expose (see Decision).
- **A flat `SolverListener` package placement (`io.github.rcrida.jcsp.solver`, alongside
  `PropagationFixpointSolver` et al.) instead of a `solver.listener` subpackage.** The `solver`
  package had grown to 28 files even before this change; moving the listener family (five
  interfaces) into `solver.listener` follows the same subpackaging convention already used for
  other cohesive groups (`solver.assignmentfactory`, `solver.backtrackingsearch.*`, `solver.tree.*`),
  at the cost of one import at each of the ~12 call sites that reference these types.

## Consequences

- Any new terminal solver added to either chain gets progress reporting "for free" by threading a
  `listener`/`SolverListener` (or `LocalSolverListener`) field through construction, the same way
  `statistics` and `limits` already are — not a new mechanism to design.
- `assignments` now has a one-way dependency on `solver.listener` (via `Assignment.listener`), where
  previously `assignments` had zero imports from `solver`. Accepted as the minimal-plumbing choice:
  `Assignment` is the one place `Statistics`-style token-threading already lives, and duplicating
  that machinery elsewhere just to avoid the new edge would be worse than the edge itself.
- Fixed, incidentally, while wiring `onNodeExplored`: `MinConflictsSolver`/`TabuSearchSolver` were
  using `Assignment#withValue` (which increments `Statistics#nodesExplored`) for *candidate*
  evaluation but plain `toBuilder()` (no increment) for the *real* accepted move — the reverse of
  what's correct, and inconsistent with `WalkSATSolver`, which already used `withValue` correctly
  for its one real per-step flip. Both now use `withValue` only for the real move, `toBuilder()`
  only for throwaway candidate scoring, making `Statistics#nodesExplored`/`onNodeExplored` a
  consistent "one event per real step" signal across all three solvers.
  `LargeNeighborhoodSolver` is unaffected: its multi-variable-combo-per-step shape has no single
  `(variable, value)` pair to report and deliberately bypasses `Assignment`'s builder machinery for
  performance (see its own Javadoc), so it reports solely via `onLocalSearchStep`.
