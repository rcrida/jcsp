# 0011. Cancellation token for main-chain search

**Status**: Accepted

## Context

`Cancellation` (`io.github.rcrida.jcsp.solver`) was a package-private cooperative cancellation
token used only by `RaceLocalSolver` to stop the losing delegate in a local-search race
(`MinConflictsSolver`/`TabuSearchSolver` implement `CancellableLocalSolver`, checking
`cancellation.isCancelled()` once per step). It had no path into the main backtracking/branch-and-
bound chain and wasn't public, so external callers couldn't construct or register one.

The goal: let a caller cancel a `Solver`'s search from outside — construct a `Cancellation`, hand it
to a `SolverListener` implementation (as a field it holds) and to `SolverConfig`, and call
`cancel()` from a listener callback once it observes something worth stopping for (see ADR-0010 for
the listener mechanism this plugs into).

Two things came up while scoping this:

1. **Cancellation had to be checked *inside* `FixpointPropagation`'s propagator loop, not just once
   per search node.** A single fixpoint round runs every entry in `PROPAGATORS` (~38 propagators)
   in sequence, and this loop is the hot path invoked at every search node (via
   `Solver.Factory#FULL_PROPAGATION_INFERENCE`), once per `SetBranchingSolver` branch step, and once
   for whole-CSP preprocessing (`PropagationFixpointSolver`) — on a large/hard CSP a single round can
   itself run long enough that a signal arriving mid-round should interrupt it, not just be noticed
   at the next node.
2. **`SetBranchingSolver` had no `SolverLimits` awareness at all.** Each of its `forceIn`/
   `excludeFrom` branch decisions is the set-CP analogue of a node in `DomWdegLubySearch`/
   `BranchAndBoundSolver`'s tree, so this was closed alongside cancellation rather than left as a gap
   only one of the two stop mechanisms covered.

## Decision

- **`Cancellation` becomes public** (`cancel()`/`isCancelled()`/`NEVER`). Since it now carries real
  mutable state exposed as public API, calling `cancel()` on `NEVER` — previously prevented only by
  internal-code discipline, since `RaceLocalSolver` always constructs a fresh `Cancellation` per race
  and never touches `NEVER` — is upgraded to a hard failure (`UnsupportedOperationException`) rather
  than silently poisoning the shared sentinel every unconfigured solve defaults to.
- **`SolverConfig` gains a `cancellation` field** (`@Builder.Default Cancellation.NEVER`), threaded
  the same way `limits`/`listener`/`statistics` already are: read once at construction by
  `Solver.Factory.INSTANCE` and passed by reference into every builder that checks it.
- **`FixpointPropagation.applyFixpoint`/`applyFixpointWithReason` gain two more explicit
  parameters, `Statistics` and `Cancellation`** — the same treatment `listener` already got in
  ADR-0010, for the same reason: this is a stateless static utility called from three structurally
  different places with no shared instance to hang state off. `cancellation` is checked once per
  propagator within the round ("between propagators"), throwing `SolverCancelledException` (built
  from `statistics`) the moment it's detected.
- **`SolverCancelledException` mirrors `LimitExceededException`'s exact existing asymmetry**: it is
  thrown *only* from `DomWdegLubySearch.getSolution()`'s Luby-restart-driven single-solution search
  — the one call path in the whole chain with a genuinely distinct single-result algorithm of its
  own, rather than one defined purely as consuming `getSolutions()`'s stream (`BranchAndBoundSolver`,
  `SetBranchingSolver`, and generic `Solver`/`SolverDecorator` defaults all just `findFirst()`/
  `reduce()` over the stream). Every other path — both chains' `getSolutions()`,
  `BranchAndBoundSolver.getSolution()`, `PropagationFixpointSolver`, `SetBranchingSolver` — stops
  silently instead, `catch`ing `SolverCancelledException` around whatever call into
  `FixpointPropagation`/`inferOrExplain` might throw it and converting to their own existing
  "stopped early" behavior (`Stream.empty()`/`Optional.empty()`/filtering the candidate out).
  No new private sentinel exception type was introduced for this — `SolverCancelledException` (the
  real public type) is thrown directly at every detection site and caught where silence is required;
  there is nothing to translate, so a separate internal-only signal type would be pure overhead.
- **A consequence of the above, confirmed and accepted rather than special-cased**: because
  `PropagationFixpointSolver`'s one-time preprocessing pass runs before `DomWdegLubySearch` ever gets
  control, and it always catches-and-silences `SolverCancelledException` (having no distinct
  single-solution algorithm of its own either), a cancellation that fires *during preprocessing* —
  including a token that was already cancelled before the call even started — is always silent, even
  for the satisfaction chain's `getSolution()`. `SolverCancelledException` can only surface when
  cancellation happens specifically while `DomWdegLubySearch`'s own backtracking search is active,
  after preprocessing has already converged. A caller that needs to know *why* a solve stopped can
  always inspect the `Cancellation` token it holds itself, regardless of which path was taken.
- **`Assignment` gains a fourth record component, `cancellation`**, mirroring exactly how `statistics`
  and `listener` already ride on `Assignment` (ADR-0010): `Solver.Factory#FULL_PROPAGATION_INFERENCE`
  is a static singleton `Inference` with no per-solve state, so `assignment.cancellation()` is how a
  per-solve cancellation token reaches that one call site. `Assignment.ofTrusted` gains `cancellation`
  as a required fourth parameter for the same reason `statistics`/`listener` are required there (a
  caller deriving from an existing `Assignment` must carry its token forward explicitly); its one
  caller, `LargeNeighborhoodSolver`, is updated mechanically (it doesn't otherwise participate in
  cancellation and isn't gaining any new capability from this).
- **`SetBranchingSolver` gains `limits`, `cancellation`, and `statistics` fields.** Each branch step
  (`forceIn`/`excludeFrom` descent, extracted into a new `branch` helper) increments the *same
  shared* `statistics.incrementNodesExplored()` the terminal solvers already increment via
  `Assignment#withValue`, then checks it against `limits`/`cancellation` before repropagating —
  mirroring `DomWdegLubySearch`/`BranchAndBoundSolver`'s own per-candidate check exactly. Since
  `SetBranchingSolver` never builds an `Assignment` (it narrows `SetBoundedDomain` bounds directly on
  the CSP, not via variable assignment), it can't reuse `withValue`'s side effect, so
  `Statistics#incrementNodesExplored` is widened from package-private to public — a small, deliberate
  encapsulation loosening in exchange for one true unified node count across the whole solve, rather
  than an independent local counter that would leave `Statistics` an incomplete picture of total
  search effort. `SetBranchingSolver` has no distinct `getSolution()` of its own (it's just
  `getSolutions(csp).findFirst()`), so it never throws under either entry point, consistent with the
  asymmetry above.

## Rejected alternatives

- **"Always throw everywhere"** (`SolverCancelledException` from every `getSolution()`/
  `getSolutions()`, both chains) — simpler to implement (one throw site, no catch/convert
  boilerplate at every other call site of the shared `FixpointPropagation` utility), but breaks the
  established "streams truncate silently, only one specific `getSolution()` throws" contract
  `LimitExceededException` already has. Rejected in favor of mirroring that contract exactly.
- **Threading a "throw-capable context" flag down through `FixpointPropagation`/
  `PropagationFixpointSolver`** so cancellation during preprocessing could still throw specifically
  when reached via the satisfaction chain's `getSolution()`. Rejected: `PropagationFixpointSolver`'s
  `preprocess()` hook is shared identically by `SolverDecorator`'s `getSolutions()`/`getSolution()` by
  design, so distinguishing entry points there would mean breaking that shared-hook shape just for
  this one signal — accepted the resulting behavior (see Decision) instead.
- **An independent local branch-step counter in `SetBranchingSolver`**, checked against the same
  shared `SolverLimits` config value but not folded into `Statistics`. Simpler (no visibility
  change needed), but leaves `Statistics#getNodesExplored` an incomplete picture of the whole solve's
  search effort. Rejected in favor of widening `incrementNodesExplored` to public.
- **A private sentinel exception type** (mirroring `DomWdegLubySearch`'s own `BudgetExceeded`/
  `LimitsExceeded`, thrown internally and converted to the public exception only at the true
  boundary). Rejected once it became clear every non-canonical call site needs to catch *something*
  and convert it to silence regardless of whether that something is a private sentinel or the real
  public exception — using the real exception directly removes an entire class and a conversion step
  for no loss of clarity.

## Consequences

- Any future terminal solver or branching decorator added to either chain gets `limits`/
  `cancellation` checks "for free" by following the same pattern documented here — not a new
  mechanism to design each time.
- `assignments` now has a second one-way dependency on `solver` directly (via
  `Assignment.cancellation`'s `Cancellation` type), alongside the existing one on `solver.listener`
  from ADR-0010. Accepted for the same reason: `Assignment` is the one place this token-threading
  machinery already lives, and duplicating it elsewhere to avoid the edge would be worse than the
  edge itself.
- A caller that cancels during the (often dominant, per `PropagationFixpointSolver`'s own Javadoc)
  preprocessing phase gets a silent `Optional.empty()`/truncated stream, indistinguishable from
  genuine infeasibility from the return value alone — exactly the pre-existing ambiguity
  `LimitExceededException` already lives with for every path except `DomWdegLubySearch.getSolution()`.
  A caller that needs to disambiguate should check its own `Cancellation` reference rather than rely
  on the return value/exception alone.
