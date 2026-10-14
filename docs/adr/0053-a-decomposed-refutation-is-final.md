# 0053. A decomposed refutation is final, and the decomposition declines as soon as it is too wide

**Status**: Accepted

## Context

`Dubois-050` and `Dubois-100` (XCSP3 `Dubois` series, unsatisfiable) neither finished within a
30s budget nor, in the first case, honoured it: the process ran past 150s on a 10s limit. Chasing
that turned up four separate faults in the tree-decomposition path, two of them hiding the others.

**1. `TreeSolver` never checked `Cancellation`.** Its nested `flatMap` search ran to the end of its
stream whatever the token said. A thread dump put every sample inside `populateAssignment`.

**2. The tree CSP could not refute.** `TreeDecomposerImpl` joined two adjacent cliques with one
`AssignmentVariableConsistencyConstraint` *per shared variable*. `AC3.revise(problem, arc)` revises
each constraint on an arc separately, so a clique value survived on the strength of a different
neighbouring value for every shared variable, instead of one neighbouring value agreeing on all of
them. That is weaker than the arc consistency a tree needs to be backtrack-free. On `Dubois-050` the
directional pass left the tree arc-consistent enough to start searching and then thrashed: 144,200
search nodes in 12s, all but four at the same depth, for an instance a correct pass refutes before
any node is visited.

**3. A refutation was discarded.** `TreeDecompositionSolver.getSolution` was
`decompose(...).flatMap(tree -> treeSolver.getSolution(tree)).or(() -> inner.getSolution(csp))`.
`Optional.flatMap` collapses "decomposed, and the tree solver proved it unsatisfiable" and "not
decomposed" into the same empty value, so a proof fell through to the inner search, which redid the
whole problem the slow way. `getSolutions` had no such fall-through, so the two methods disagreed.
Fault 3 is why fixing fault 2 alone did not help: the stack sample at 12s was no longer in
`TreeSolver` at all but in `DomWdegLubySearch`.

**4. The decomposer built a decomposition it was about to throw away.** It ran min-degree
elimination to the end, then an O(m²) maximal-clique filter, then an O(m²) clique-edge pass, and only
then checked the clique-size cap ([ADR-0042](0042-tree-decomposition-cost-control.md)) and returned
empty. None of it checked `Cancellation`. On a graph far wider than the cap that is pure waste:
`bmc-ibm-4-04_X2` ran 70s on a 10s limit, with stack samples walking through `selectVertex`, the
maximal-clique filter and `getCliqueEdges` in turn.

## Decision

**`TreeSolver` takes a `Cancellation` and checks it at each node**, truncating silently like every
`getSolutions()` stream. The single-solution callers that must tell a cancelled search from an
unsatisfiable one check the token themselves: `CutsetConditioningSolver.getSolution`'s all-tree path
and `TreeDecompositionSolver.getSolution` both throw `SolverCancelledException`
([ADR-0043](0043-inconclusive-is-not-unsatisfiable.md)).

**One consistency constraint per tree edge, carrying the whole separator.**
`AssignmentVariableConsistencyConstraint` holds a set of shared variables and is satisfied only when
every one agrees. Arc consistency then revises the pair against a single relation, as a tree
requires. Values compare with `equals`; the previous `==` was reference equality on boxed values,
correct only inside the `Integer` cache.

**An empty tree result is the answer.** Once a problem has been decomposed, `getSolution` returns
the tree solver's result as it stands. It no longer falls through to `inner`; it throws
`SolverCancelledException` when the token was cancelled. `TreeDecompositionSolver` takes the
`Cancellation` and `Statistics` for that, as `CutsetConditioningSolver` does.

**The cap is checked while the bags are built.** Each bag's joint domain size is computed as
elimination produces it, and the decomposition is declined at the first bag over the cap. Every
maximal clique is one of those bags, so this declines exactly what the late check declined, sooner.
Elimination checks `Cancellation` once per vertex. The late per-clique check became unreachable and
was removed.

## Rejected alternatives

**Joint revision inside `AC3.revise`**, treating all constraints on an arc as one relation. It would
have fixed fault 2 for any caller, but `AC3` is the propagator used everywhere in the solver and
each constraint on an arc is deliberately its own propagator; changing that is a far larger and
riskier edit than giving the tree CSP the one constraint it needed. Not built.

**Keeping the fall-through to `inner` as a safety net.** It would have masked a decomposition that
wrongly claims unsatisfiable, and that is the one real argument for it. It also made every decomposed
unsatisfiable problem pay for the whole problem twice, and it was the reason fault 2's fix showed no
benefit at first. A false UNSAT from the decomposition is now a bug to find rather than to absorb —
see Consequences.

## Consequences

`Dubois-050` and `Dubois-100` go from timing out to `s UNSATISFIABLE` in about a second. The tree
solve of any decomposed problem is now a complete procedure whose empty answer is final.
`bmc-ibm-4-04_X2` stops at its limit (11s on a 10s limit, down from 70s) because the decomposition is
declined early. The 85-instance competition corpus completed with no failed or wrong answers
(84 solved, 1 unknown, 773s wall-clock), and the single unsatisfiable claim it contains
(`testExtension3`) was confirmed by the independent Choco harness.

**The safety net is gone.** A bug that makes a decomposition wrongly unsatisfiable now surfaces as a
wrong `s UNSATISFIABLE` instead of a slow correct answer. The corpus has few unsatisfiable instances,
so it is a weak check of this path; the cross-solver harness
(`jcsp-choco-bench`, which found three false UNSATs jcsp's own tests could not) is the real oracle,
and a change to decomposition should be run through it.

The early decline is exact only while every domain has at least one value, because a bag with an
empty-domain variable has joint size 0 and a superset containing it would not exceed the cap.
Preprocessing refutes an empty domain before this code is reached, so the difference is unreachable
in a chain solve.

`Cancellation`'s Javadoc lists its check sites; `TreeSolver` and the elimination loop join that list.
