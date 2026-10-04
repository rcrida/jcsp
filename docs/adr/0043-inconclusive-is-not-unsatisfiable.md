# 0043. "Inconclusive" is not "unsatisfiable"

**Status**: Accepted

## Context

`BoundSolver.getSolution()` returned `Optional.empty()` for two incompatible reasons: a search that
ran to completion and proved there is no solution, and a search that stopped early having proved
nothing. Two paths produced the second kind:

- **`PropagationFixpointSolver.runFixpoint`** caught `SolverCancelledException` and returned
  `Optional.empty()`. Its own Javadoc said so: *"indistinguishable from genuine infeasibility here"*.
- **`DomWdegLubySearch.getSolution`** returned `Optional.empty()` after exhausting `maxRestarts`.
  Every restart is cut off by a failure budget, so an abandoned one proves nothing; running out of
  them says only that no attempt was ever given enough budget to finish.

This was not a missing feature, it was a wrong answer. `Xcsp3ProblemRunner.solveSatisfaction`
inferred UNSAT from an empty result and printed **`s UNSATISFIABLE`** for a problem it had never
refuted — on a *satisfiable* instance, if the cancellation landed during preprocessing. A test
asserted that behaviour (`getSolutionReturnsEmptySilently_whenCancelledBeforeSearchStarts`) and its
Javadoc explicitly noted the result was "the same as" the genuine-UNSAT case, so the conflation was
recorded as intended rather than noticed as a bug.

It also blocks the next thing worth building. A bounded first-solution search — "is there a solution
costing at most *m*?", the dichotomic step that would tighten branch-and-bound's starting incumbent
([ADR-0041](0041-reuse-the-satisfaction-search-for-the-first-solution.md)) — must distinguish "no
solution at *m*" from "gave up at *m*". Treating the second as the first raises the lower bracket on
no evidence and converges below the true optimum: a wrong optimum, not a slow one. Measuring
`Taillard-js-015-15-0` showed that is not a theoretical worry but the normal case, since a band of
~55 objective units around the optimum returns neither answer within a 60s budget.

## Decision

**`Optional.empty()` from `getSolution` means proven unsatisfiable, and nothing else.** Every way of
stopping early throws.

A new `abstract InconclusiveSearchException` (carrying `Statistics`) becomes the supertype of the two
existing exceptions, so a caller that only cares *that* the search stopped catches one type:

| exception | means |
|---|---|
| `LimitExceededException` | a pre-configured `SolverLimits` budget was hit |
| `SolverCancelledException` | an external `Cancellation` fired |
| `RestartsExhaustedException` (new) | a restart cap was reached with no attempt completing |

Inserting a supertype above the existing two is source-compatible: `catch (SolverCancelledException)`
still compiles and still catches.

`PropagationFixpointSolver` stops swallowing the cancellation. The silence that
`getSolutions()` promises moves to **`SolverDecorator.getSolutions`**, which catches
`InconclusiveSearchException` around `preprocess` — the one place that owns the stream contract.
Putting it there rather than in the producer is what lets the single-solution path see the truth,
since the two paths share `preprocess`.

`Xcsp3ProblemRunner.solveSatisfaction` catches the supertype and reports `s UNKNOWN`.
`BranchAndBoundSolver.seedIncumbent` catches it too, replacing a two-type catch; it does not need the
distinction itself, but it is now available to anyone who does.

## Rejected alternatives

**A result type** — `SolutionResult` carrying an `Optional<Assignment>` plus an outcome enum of
`SOLVED`/`UNSATISFIABLE`/`UNKNOWN`, returned by a new `solve(csp)` method. More informative at the
call site, and it avoids exceptions for a non-exceptional outcome. Rejected because
[ADR-0011](0011-cancellation-token-for-main-chain-search.md) already settled the shape of this: the
single-solution searches throw when they stop early, `getSolutions()` truncates. Adding a parallel
API would leave two ways to ask the same question, with `getSolution` still lying in the cases
nothing had migrated off it. This completes the existing contract instead of competing with it.

**Reusing `LimitExceededException` for restart exhaustion.** No new type, and callers already catch
it. Rejected because no `SolverLimits` was exceeded — the exception's own Javadoc says that is what it
means — and a public exception type that misreports why it was thrown is worse than one more class.

**Leaving `getSolutions()` to throw as well**, for symmetry. That breaks the documented
silent-truncation contract every decorator and the optimization chain rely on, for no gain: a stream
consumer already learns that the stream ended.

## Consequences

A caller that treated `Optional.empty()` as "no solution, or we gave up" now receives an exception on
the second case. That is the point, but it is a behaviour change for anyone relying on the old
conflation, and the two tests asserting it were rewritten.

`RestartsExhaustedException` is unreachable from `createSolver(csp)`: the satisfaction chain sets
`maxRestarts` to `Integer.MAX_VALUE` precisely so `SolverLimits` stays the only bound there. It is
reachable for a caller that embeds that search as one bounded phase of a larger one, which is exactly
what `BranchAndBoundSolver`'s first-solution search does.

Corpus at 60s is unchanged — **84 solved, 1 unknown, 0 failed** — and no instance moved between
`UNSATISFIABLE` and `UNKNOWN`, which says the bug was latent on this corpus rather than active: no
bundled instance currently spends its whole budget inside preprocessing. That is luck, not a defence.

What this unblocks: a bounded probe can now treat a thrown result as "no information, stop
descending" and an empty result as a genuine refutation it may trust, which is the precondition for
tightening a lower bound rather than only accepting better incumbents.
