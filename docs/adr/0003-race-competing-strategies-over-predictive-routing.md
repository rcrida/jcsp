# 0003. Race competing strategies instead of predictive routing

**Status**: Accepted

## Context

Several places in jcsp have more than one viable algorithm for the same job, with no reliable way
to know in advance which one will perform better on a given problem instance: `MinConflictsSolver`
vs. `TabuSearchSolver` for local-search repair, and (in principle) `BacktrackingSearch` vs.
`DomWdegLubySearch` for systematic search. A router that inspects problem shape (variable count,
constraint density, constraint types) and picks a strategy up front is the obvious first idea, but
it requires a heuristic that's actually predictive — and predicting solver performance from static
problem features is a well-known hard problem in its own right.

## Decision

Where genuine performance prediction is the blocker, don't build a router: run the competing
strategies concurrently and take whichever finishes first. `RaceLocalSolver` races an arbitrary list
of `LocalSolver` delegates (currently `MinConflictsSolver` + `TabuSearchSolver`) on virtual threads.
Delegates implementing the package-private `CancellableLocalSolver` contract are handed a shared
`Cancellation` token, tripped as soon as any delegate wins, so losers stop at their next search step
instead of running to completion for no benefit. A delegate returning `Optional.empty()` doesn't end
the race — only once every delegate has finished without a result does the race conclude empty.

This is a distinct axis from *structural* routing, which jcsp also uses and which this decision
doesn't replace: `LocalSolver.Factory` routes to `WalkSATSolver` when every domain is boolean *and*
the CSP contains no `ExactlyOneConstraint`/`AtLeastNConstraint` (a hard structural precondition
WalkSAT's move shape can't handle otherwise, not a performance guess), and routes to
`LargeNeighborhoodSolver` when the reduced CSP contains any `ExactlyOneConstraint` (LNS's
destroy-repair moves over exactly-one slots are a structural fit that plain `MinConflictsSolver`
can't express as directly). Structural routing is reliable because the criterion is a hard
precondition, not a performance prediction; racing is used specifically where no such hard
precondition distinguishes the candidates.

## Rejected alternatives

- **A predictive router for `BacktrackingSearch` vs. `DomWdegLubySearch`** (systematic search).
  Tried and falsified: no problem-shape or constraint-type heuristic reliably predicted which one
  would win, so no router was built for that pair — `DomWdegLubySearch` is used unconditionally in
  the satisfaction chain (ADR-0001), and `BacktrackingSearch` stays a standalone, independently-tested
  implementation not wired into production. See `project_jcsp_backtracking_vs_domwdeg_routing` in
  project memory — don't re-propose a router for this pair without genuinely new evidence.
- **A similar predictive router for `MinConflictsSolver` vs. `TabuSearchSolver`.** Not attempted,
  given the above precedent; racing was adopted directly instead.

## Consequences

- Adding a new local-search delegate to the race means implementing `CancellableLocalSolver` if it
  should stop promptly on losing, and confirming it's safe to run concurrently with the others
  (shared nothing but the `Cancellation` token).
- A delegate's `RuntimeException`/`Error` is rethrown as its real type from the race (fixed
  2026-07-18 — an earlier version unconditionally cast to `RuntimeException`, turning a real `Error`
  elsewhere in the solver chain into a misleading `ClassCastException`).
- The structural-vs-performance routing distinction is a real design line: before adding a new
  routing rule, check whether the distinguishing criterion is a hard precondition (route) or a
  performance guess (race) — conflating the two risks either an unreliable heuristic or needlessly
  running two solvers when one is structurally guaranteed to fail.
