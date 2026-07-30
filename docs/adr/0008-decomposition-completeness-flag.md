# 0008. Decomposition-completeness flag for binary decompositions

**Status**: Accepted

## Context

Several n-ary constraints (`AllDiffConstraint`, `AtMostOneConstraint`/`ExactlyOneConstraint`,
`IncreasingConstraint`/`DecreasingConstraint`, `ReifiedConstraint`, `CircuitConstraint`) implement
`BinaryDecomposable#getAsBinaryConstraints()`, letting `ConstraintGraph` infer additional binary
constraints for AC3 propagation. But a pairwise decomposition isn't always a *complete* stand-in for
the original constraint's `isSatisfiedBy`: `ExactlyOneConstraint`'s inherited pairwise-NAND
decomposition only rules out two-true, never zero-true; `CircuitConstraint`'s all-different
decomposition only rules out repeated successors, never a sub-tour. Code that used a decomposition
as a *replacement* for the real constraint (rather than as additional propagation help alongside it)
could reach a state the decomposition considers fine but the real constraint doesn't.

This surfaced as a real bug: `LocalSearchSupport#conflictConstraints` (shared conflict-repair scoring
for `MinConflictsSolver`/`TabuSearchSolver`/`WalkSATSolver`) unconditionally preferred a constraint's
`BinaryDecomposable` decomposition over the constraint itself, for per-pair scoring granularity. A
search could reach, e.g., every variable in an `ExactlyOneConstraint` slot false and see zero
tracked violations, since the decomposition's gap can't express the missing whole-constraint
condition. `Assignment#isSolution` (the actual accept/reject check) always used the real constraint
directly, so this never produced a *wrong final answer* — but `weighConflicts` had no signal to move
away from such a state, degrading local search to random tie-breaking on that dimension instead of
directed repair.

## Decision

Add `BinaryDecomposable#isDecompositionComplete()` (default `true`): whether
`getAsBinaryConstraints()` alone is a sound *and complete* stand-in for the constraint's own
`isSatisfiedBy` (every decomposed binary constraint satisfied iff the original is), not merely a
necessary condition. `ExactlyOneConstraint` and `CircuitConstraint` override it `false`.
`LocalSearchSupport#conflictConstraints` now keeps the original n-ary constraint alongside an
incomplete decomposition rather than dropping it, restoring a real signal for those cases.

`AC3`/`ConstraintGraph` never consult this flag — they only ever use a decomposition as *additional*
propagation alongside the original constraint, never as its replacement, so incompleteness doesn't
matter there. `conflictConstraints` is the one caller that substitutes the decomposition *for* the
original when scoring, which is exactly why it needed the flag.

## Rejected alternatives

- **Leaving `conflictConstraints`'s unconditional decomposition preference as-is** and accepting the
  degraded repair signal. Rejected once the mechanism of the bug was understood — the fix (keeping
  both) was cheap once the actual gap (decomposition incompleteness, not a scoring-logic bug per se)
  was correctly diagnosed.
- **Making every decomposition complete** (e.g. giving `ExactlyOneConstraint`'s decomposition an
  explicit "at least one" binary encoding). Not pursued — some conditions (a whole-set counting
  condition, a global cycle-freedom condition) don't have a natural pairwise-binary encoding at all,
  so the flag-and-keep-both approach generalizes better than forcing every decomposition to be
  complete.

## Consequences

- Any future `BinaryDecomposable` implementation must audit whether its own decomposition is
  actually complete before defaulting to `true` — the default is optimistic, and getting this wrong
  silently reintroduces the same class of bug for a new constraint type.
- This is the same shape of gap that motivated giving `AtMostOneConstraint`/`ExactlyOneConstraint`
  (2026-07-17), `IncreasingConstraint`/`DecreasingConstraint` (2026-07-18), and
  `ReifiedConstraint`/`ImplicationConstraint` (2026-07-20) real `Propagatable` implementations
  independently of this flag — a whole-constraint condition invisible to whatever partial mechanism
  covered it before (a counting condition, `BoundedDomain`/fixpoint reach, an n-ary body) needed
  fixing at the propagation layer, not just the local-search scoring layer. The `AtMostOneConstraint`/
  `ExactlyOneConstraint` gap specifically was found via a boolean-encoded pigeonhole scenario in
  `NogoodPropagationBenchmark` and also motivated the CDCL-side investigation in ADR-0002.
- `WalkSATSolver`'s `if (unsatisfied.isEmpty()) break;` branch became provably dead code once this
  fix landed (`conflictConstraints` is now a faithful stand-in for true satisfiability, so that check
  could only fire at a point already ruled out by an earlier `isSolution` check) and was removed
  rather than left permanently uncovered.
