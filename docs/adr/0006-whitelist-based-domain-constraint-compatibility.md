# 0006. Whitelist-based domain/constraint compatibility validation

**Status**: Accepted

## Context

Not every constraint type has a sound interpretation over every domain kind. A `BoundedDomain`
(continuous interval) has no meaningful notion of "all different" (real numbers are dense, so any
two overlapping positive-width intervals contain infinitely many distinct values — the constraint is
nearly always trivially satisfiable except on exact singleton collisions), and several propagators
do unconditional `DiscreteDomain` casts that would crash outright on a `BoundedDomain`/`SetBoundedDomain`
variable. Left unchecked, a caller combining an incompatible constraint/domain pair would either get
a silently-meaningless result or a deep, confusing crash inside a propagator far from where the
mistake was made.

## Decision

`ConstraintSatisfactionProblem`'s build-time validation maintains two explicit whitelists —
`CONTINUOUS_COMPATIBLE_CONSTRAINTS` for `BoundedDomain` and `SET_COMPATIBLE_CONSTRAINTS` for
`SetBoundedDomain` — and rejects any other constraint type referencing a variable of the
corresponding domain kind with `IllegalArgumentException` at build time, before the CSP is ever
solved. Each whitelist entry is a concrete class, not an interface, since compatibility is a
property of the specific implementation (e.g. `NogoodConstraint`'s own implementations are
whitelisted individually — ADR-0002). `ReifiedConstraint`/`ImplicationConstraint` need no entry of
their own: `validateCompatibility` recurses into their `body` instead of checking the wrapper's own
class, so a reified constraint is validated as if its body were registered directly.

Deliberately excluded from `CONTINUOUS_COMPATIBLE_CONSTRAINTS`: `AllDiffConstraint`,
`CountConstraint`, `AmongConstraint`, `GlobalCardinalityConstraint` — the density-of-reals argument
above means the marginal propagation value doesn't justify guarding Régin's matching algorithm
(which does an unconditional `DiscreteDomain` cast and would otherwise crash). `InverseConstraint`,
`CircuitConstraint`, `RegularConstraint`, `NaryTuplesConstraint` are excluded for a different reason:
they're fundamentally index/table/sequence-shaped, and continuous semantics simply don't apply to
what they express.

## Rejected alternatives

- **Let an incompatible constraint/domain pair fail wherever it happens to fail** (a propagator's
  unconditional cast, or silent no-op). Rejected: pushes the failure far from its cause, and for
  domain kinds like continuous `AllDiff` produces a misleadingly "successful" but meaningless result
  rather than any failure at all.
- **Pursuing `AllDiffConstraint` support over `BoundedDomain` anyway.** Explicitly declined by the
  project owner after the density-of-reals tradeoff was explained — don't re-propose without a new
  motivating use case (see `project_jcsp_continuous_domain_support_status` in project memory).

## Consequences

- Adding a new constraint type that should support `BoundedDomain` or `SetBoundedDomain` requires an
  explicit whitelist entry, plus checking (per the same project-memory note): (1) does it implement
  `Propagatable` — if so, inspect its `propagate()` for unconditional `DiscreteDomain` casts (crash
  risk); (2) if it connects 3+ variables, test it standalone to confirm it doesn't get short-
  circuited before reaching `TreeDecompositionSolver`, which is the exact path a real latent bug
  (`TreeDecomposerImpl.getMaximalCliqueBags` crashing on 3+ mutually connected `BoundedDomain`
  variables) was found through when `NaryElementConstraint` was whitelisted.
- The whitelist is deliberately a safety net, not a completeness signal: many whitelisted
  constraints (predicates, reification) are safe not because they have special-cased continuous
  propagation logic, but because they aren't `Propagatable`/`BinaryDecomposable` at all — correctness
  rests entirely on the final `isSatisfiedBy` check every solver path runs before returning a
  solution.
