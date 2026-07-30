# 0001. Two-chain decorator-based solver architecture

**Status**: Accepted

## Context

jcsp needs to support two distinct solving modes over the same `ConstraintSatisfactionProblem`
model: enumerating/finding satisfying assignments, and finding an assignment that optimizes an
objective function. Both modes share almost the entire preprocessing pipeline (node consistency,
propagation to fixpoint, decomposition for structurally easy subproblems) but diverge in how they
handle domains that remain non-singleton after propagation, and in what their terminal search step
does with that state.

Duplicating the whole pipeline per mode would mean every future propagator or preprocessing step
has to be added twice and kept in sync. A single monolithic solver class handling both modes via
internal branching would tangle satisfaction-only concerns (independent subproblem decomposition,
tree decomposition, cutset conditioning) with optimization-only ones (bisection over continuous
domains, incumbent tracking) in one place.

## Decision

Build two separate decorator chains from a shared set of `SolverDecorator` stages, each producing a
`BoundSolver` with the CSP already bound:

- **Satisfaction** (`createSolver(csp)`): `NodeConsistency → PropagationFixpoint(snap=true) →
  SetBranching (set variables only) → IndependentSubproblems → TreeDecomposition →
  CutsetConditioning → TreeSolver / DomWdegLubySearch`
- **Optimization** (`createSolver(csp, objective)`): `NodeConsistency →
  PropagationFixpoint(snap=false) → BisectionConditioning (continuous only) → SetBranching (set
  variables only) → BranchAndBound`

Both chains share the same `NodeConsistentSolver` and `PropagationFixpointSolver` stages (the
`snap` flag on the latter controls whether a non-singleton `BoundedDomain` gets snapped to its
midpoint for one concrete solution, or left open for `BisectionConditioningSolver` downstream).
`SolverDecorator.getSolutions()` short-circuits immediately when any preprocessing step reduces all
domains to singletons, so a highly-constrained problem never reaches the terminal search stage at
all.

`BranchAndBoundSolver` implements `Solver` directly with its own recursive search rather than
wrapping `BacktrackingSearch` or extending `SolverDecorator`: incumbent-based branch-and-bound
pruning (`objective(partial) >= incumbent`) needs to interleave with the recursion itself, which a
decorator wrapping a separate inner solver can't do cleanly.

## Rejected alternatives

- **One solver class branching internally on satisfaction vs. optimization.** Rejected: it would
  tangle satisfaction-only stages (independent-subproblem decomposition, tree decomposition,
  cutset conditioning) with optimization-only ones (bisection, incumbent tracking) in the same
  class, instead of keeping each concern in its own decorator.
- **`BranchAndBoundSolver` wrapping `BacktrackingSearch`.** Tried and removed (2026-07-06):
  `Solver.Factory`'s optimization chain used to construct a `BacktrackingSearch` solely to satisfy
  `BranchAndBoundSolver`'s inherited `SolverDecorator.inner` field, which `BranchAndBoundSolver`
  never actually called — dead wiring, removed along with the unnecessary `SolverDecorator`
  inheritance.

## Consequences

- Adding a new preprocessing step means deciding explicitly which chain(s) it belongs in, and
  where in the sequence — the ordering encodes real assumptions (e.g. `BisectionConditioningSolver`
  must run before `SetBranchingSolver` picks up remaining discrete/set work; `SetBranchingSolver`
  is wired into *both* chains, unlike `BisectionConditioningSolver`, because an arbitrary choice
  among a set variable's undetermined elements has no box-consistency guarantee the way a
  continuous midpoint snap does).
- `BranchAndBoundSolver`'s bespoke recursive implementation means CDCL/nogood-store wiring
  (ADR-0002) had to be added to it independently rather than inherited from a shared decorator —
  confirmed to be no shared code with `DomWdegLubySearch`'s equivalent wiring, just the same
  pattern applied twice.
- `BacktrackingSearch` remains a standalone, independently-tested generic implementation
  (`BacktrackingSearchTest`) not wired into any production chain — kept because it's useful in its
  own right, not because anything currently depends on it.
