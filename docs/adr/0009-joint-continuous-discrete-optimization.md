# 0009. Joint continuous/discrete optimization (LP relaxation)

**Status**: Implemented

## Context

jcsp's optimization chain (ADR-0001) hardcodes `BisectionConditioningSolver` (resolves every
`BoundedDomain` variable via bisection) *before* `BranchAndBoundSolver` (branches discrete
variables). This two-phase decomposition approximates what real MIP solvers (CPLEX/Gurobi/SCIP) do
properly: solve a joint LP relaxation over *all* variables at every search node, giving one bound
that reflects both variable kinds simultaneously, and only branch on fractional integer variables.

The fixed continuous-first order works fine when continuous variables are tightly self-coupled by
constraints — propagation collapses them fast regardless of discrete choices (e.g.
`ContinuousOptimizationTest`'s `x + y = 7`). It breaks down when a continuous variable's *useful*
bounds depend on a still-unresolved discrete decision: bisection then blindly halves the full static
range with no way to know it's irrelevant. This was found via an attempt to model MIPLIB's `flugpl`
instance (6-period airline fleet planning, 11 integer + 7 continuous variables, where integer
`STM`/`ANM` counts dominate ~80% of cost while continuous `UE` overtime hours are essentially slack
determined by them) — the modeling attempt was abandoned, but it surfaced two real, reusable fixes
that did land (see `project_jcsp_bisection_incumbent_pruning` in project memory).

There's no universally correct fixed order between the two phases — it depends on which variable
class the objective is most sensitive to, and which class's bounds are actually informative before
the other is decided.

## Decision

Build a genuine LP relaxation, scoped as an additional bounding oracle inside `BranchAndBoundSolver`
rather than a replacement for the CP propagation chain. jcsp's global constraints (`AllDiffConstraint`,
`CumulativeConstraint`, `CircuitConstraint`, `DiffnConstraint`, table/regular constraints, etc.) have
no sound linear encoding without combinatorial blowup, and their existing GAC/timetabling propagators
already handle them better than a linearization would. Dropping a constraint from a minimization's LP
only *enlarges* the feasible region, so an LP bound built from only the linear-shaped part of a
problem stays sound regardless of how much of the rest of the problem isn't linear — it's just looser
the less linear the problem is.

**What maps into the LP model**: `SumBoundConstraint`, `SumVariableConstraint`,
`LinearBoundConstraint`, `LinearVariableConstraint` become rows. Every `BoundedDomain`/
`IntRangeDomain`/discrete-numeric variable's current `[min, max]` becomes a box constraint (an
integer variable's integrality requirement is exactly what gets dropped for the relaxation — the
"R" in "LP relaxation"). Every other constraint is invisible to the LP.

**New API surface**: `BranchAndBoundSolver`'s objective is `ToDoubleFunction<Assignment>` — opaque,
so it can't be introspected for LP coefficients. This requires a new, explicit `LinearObjective` type
(coefficients over variables plus a constant) supplied alongside the existing objective. `LinearObjective`
implements `ToDoubleFunction<Assignment>` itself, so it passes straight through the *existing*
`createSolver(csp, objective, config)` overload with no new overload needed: `BranchAndBoundSolver`
detects it via `objective instanceof LinearObjective` at the one call site that needs the coefficients
(`LpModelBuilder.solve`), and everything described below activates automatically whenever a caller
passes one in place of an opaque `ToDoubleFunction`.

**Wiring**: `BranchAndBoundSolver` is the optimization chain's terminal solver unconditionally —
`Solver.Factory` no longer nests it inside `BisectionConditioningSolver`, so discrete variables get
decided *before* any continuous variable is touched, not after. Two mechanisms make this sound: (1)
per-node LP-bound pruning (a single `LpModelBuilder.solve` call reused for both the incumbent-bound
check and, per Phase 3, most-fractional-variable branching); (2) `BranchAndBoundSolver` recognises
when every non-`BoundedDomain` ("discrete") variable is decided but `BoundedDomain` ones remain open
(`isDiscreteComplete`) and resolves the continuous residual itself (`resolveContinuousResidual`):
first the exact fast path — when `objective` is a `LinearObjective`, the same node's LP solution
already gives the optimal value for every `BoundedDomain` variable the LP model covers, and with
every discrete variable already pinned that's no longer an approximation, just the residual
sub-problem's exact solution, accepted only if it's complete and passes a full consistency check
(catching a `BoundedDomain` variable that also participates in a constraint the LP can't see, e.g.
`productConstraint`) — falling back to a fresh, single-use `BisectionConditioningSolver` over just
that residual otherwise. `BisectionConditioningSolver` itself is unchanged; it's now invoked as an
internal subroutine (once per discrete-complete leaf) rather than sitting at the top of the chain.
Relies on `unassignedVariableSelector` preferring discrete variables while any remain open —
`MinimumRemainingValuesSelector` (what `Solver.Factory` always wires in) satisfies this by
construction, since a non-singleton `BoundedDomain`'s `size()` is `Integer.MAX_VALUE`, larger than
any realistic discrete domain.

**LP engine**: [ojAlgo](https://www.ojalgo.org/), taken as jcsp's first real (non-annotation)
compile-scope dependency. jcsp is MIT-licensed; ojAlgo's current releases are also MIT, so there's no
license-compatibility concern — pin an MIT-licensed release specifically to avoid any Apache-era
`NOTICE`-file attribution bookkeeping.

## Rejected alternatives

- **Statically swapping the decorator order** (branch-and-bound before bisection, or vice versa, as
  a fixed default). Rejected as a general fix: whichever fixed order is chosen will still be wrong
  for the other class of problem, since the right order is problem-dependent, not a property of the
  architecture.
- **Hand-rolled simplex** (no new dependency). Rejected: LP-bound soundness is correctness-critical —
  an unsound bound silently prunes away the true optimum rather than just running slower — and a
  hand-rolled implementation would need to independently get anti-cycling (Bland's rule), degenerate
  pivoting, and infeasibility/unboundedness detection right. Not worth that risk versus an
  actively-maintained library, especially as jcsp's first-ever real dependency.
- **Apache Commons Math's `optim.linear.SimplexSolver`**. Rejected: the `optim.linear` package is in
  legacy/maintenance mode, has no native notion of per-variable bounds (a domain's `[min, max]` has
  to be added as two extra constraint rows, rebuilt from scratch every node — works against the "one
  LP solve per search node" access pattern), and pulls in a large general-purpose numerics jar (stats,
  ODE solvers, curve fitting) for the sake of one solver class.

## Consequences

- First real (non-annotation) compile-scope dependency in jcsp's history — Maven Central consumers
  now transitively pull in ojAlgo. Worth calling out explicitly in the README alongside the existing
  dependency list.
- New public API surface: just the `LinearObjective` type. No new `createSolver` overload was needed
  in the end — `LinearObjective` implementing `ToDoubleFunction<Assignment>` was enough for it to
  flow through the existing `objective` parameter and be detected via `instanceof`.
- The chain reorder changes search behavior for every mixed continuous/discrete optimization problem
  solved via the optimization chain, not just `LinearObjective`-driven ones (a plain `ToDoubleFunction`
  objective still gets the discrete-first ordering, just without the LP fast path — its continuous
  residual always resolves via the `BisectionConditioningSolver` fallback). Landed alongside
  `FlugplTest` (`solver.examples.miplib`, transcribed from MIPLIB's `flugpl.mps`, the case that
  originally motivated this ADR — see `project_jcsp_bisection_incumbent_pruning` in project memory
  for the earlier abandoned attempt) and re-verified against the full existing suite (2219 tests,
  zero failures) rather than a hand-picked subset.
- The naive version of "just reorder the chain" (a fixed decorator swap, no per-node LP resolution)
  remains rejected for the reason given above — the two mechanisms in the Wiring section
  (per-node LP-bound pruning/branching, and `resolveContinuousResidual`'s fast-path-then-bisection
  residual handling) are what make the actual reorder sound rather than just differently wrong.
- `BisectionConditioningSolver.getSolution()` is `getSolutions().findFirst()` — the *first* improving
  point found, not the best. Using it as a subroutine (as `resolveContinuousResidual`'s fallback
  does) requires exhausting `getSolutions()` and taking the last element instead; see
  `feedback_bisectionconditioningsolver_pitfalls` in project memory for this and a related pitfall
  (its own re-propagation loop can't see non-linear constraints, so a naive fallback test with two+
  jointly-constrained continuous variables risks exponential blowup or outright unsatisfiability).

## Future work

- **Binary MIP variable-domain splitting** (`x <= floor(v)` / `x >= ceil(v)` on the most fractional
  variable, each child re-solving the LP with the narrowed bound) instead of the "most fractional
  variable, full domain enumeration" branching landed so far. Not just a bigger version of that
  change — it needs a different branch shape entirely: `BranchAndBoundSolver`'s recursion currently
  only ever pins a variable to one concrete value (`assignment.withValue(variable, value)`); a binary
  split instead narrows the variable's *domain* while leaving it unassigned, the shape
  `BisectionConditioningSolver` already uses for continuous variables (`narrow(csp, target, lo, hi)`)
  but that `BranchAndBoundSolver` has no path for today. Everything downstream of variable selection
  — nogood learning, `SolverListener#onBacktrack`, consistency-checking against `cspWithNogoods` — is
  wired for concrete per-value branches; a range branch would need its own nogood shape (probably
  `RangeNogoodConstraint`, which exists but isn't integrated into this class's CDCL loop) and new
  listener semantics, not just a new value to plug into the existing ones. Lower priority than it
  would be for a general-purpose MIP solver: binary splitting's main payoff is avoiding enumeration of
  domains too large to enumerate, and jcsp's typical CSP-style domains are small enough that full
  enumeration isn't leaving much on the table — especially since every child already gets its own
  fresh, tight LP bound (Phase 2) regardless of whether it arrived via a pinned value or a narrowed
  range. Worth revisiting if wide integer domains (hundreds/thousands of values) become a real jcsp
  workload.
