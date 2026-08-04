# 0009. Joint continuous/discrete optimization (LP relaxation)

**Status**: Decided — design only, not yet implemented

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
(coefficients over variables plus a constant) supplied alongside the existing objective. This is
additive: the existing `ToDoubleFunction<Assignment>` overload remains for nonlinear objectives; a
new `createSolver(csp, LinearObjective, config)` overload opts into LP-bound pruning.

**Wiring**: default-on whenever a `LinearObjective` is supplied and the CSP has `BoundedDomain` or
relaxable discrete-numeric variables — an LP model is built and solved once per B&B search node,
giving a strictly tighter (but still sound) lower bound than today's
`objective.applyAsDouble(assignment) >= incumbent[0]` partial-assignment check, and replacing
`BisectionConditioningSolver`'s blind bisection for the part of the problem the LP model covers. Once
every discrete variable in a branch is fixed, solving the LP over the remaining continuous
sub-problem is already exact — no bisection needed for continuous variables fully covered by the
linear model. `BisectionConditioningSolver` isn't deleted, but narrows to continuous variables that
also participate in constraints the LP can't see (e.g. `productConstraint`/`divisionConstraint`/
`comparatorConstraint` chains). Branching switches to picking the *most fractional* variable from the
LP's optimal solution (standard MIP branching) rather than blind interval bisection or plain MRV,
whenever an LP model is active.

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
- New public API surface: a `LinearObjective` type and a `createSolver(csp, LinearObjective,
  SolverConfig)` overload, additive to the existing `ToDoubleFunction<Assignment>` overload.
- Because wiring is default-on (not opt-in), every existing mixed continuous/discrete optimization
  test's search behavior can shift once this lands — ship alongside a regression test modelling
  MIPLIB's `flugpl` instance (the case that originally motivated this ADR — see
  `project_jcsp_bisection_incumbent_pruning` in project memory for the earlier abandoned attempt) and
  re-verify `ContinuousOptimizationTest`, `Prob061JobShopSchedulingTest`, and any other test that
  exercises `BisectionConditioningSolver`.
- Don't re-propose "just reorder the chain" for a mixed continuous/discrete optimization problem —
  that was the rejected alternative this ADR replaces. The real starting point is the LP model
  builder described above (collect linear constraints + variable bounds into an ojAlgo
  `ExpressionsBasedModel`), not a reordering of `BisectionConditioningSolver` and
  `BranchAndBoundSolver`.
