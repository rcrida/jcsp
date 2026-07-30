# 0009. Joint continuous/discrete optimization (LP relaxation)

**Status**: Proposed — not started

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

Not yet made. This ADR exists to record the open question and rule out the tempting wrong answer
("just reorder the two decorators"), which doesn't have a fixed correct direction either. A real fix
needs either a heuristic that inspects the specific problem to pick an order, or — properly — a
genuine LP relaxation solved jointly at each search node, which is a materially bigger undertaking
than reordering two existing decorators.

## Rejected alternatives

- **Statically swapping the decorator order** (branch-and-bound before bisection, or vice versa, as
  a fixed default). Rejected as a general fix: whichever fixed order is chosen will still be wrong
  for the other class of problem, since the right order is problem-dependent, not a property of the
  architecture.

## Consequences

- Don't re-propose "just reorder the chain" for a mixed continuous/discrete optimization problem
  without first checking which variable class dominates the objective and which one's bounds depend
  on the other — that determines whether either fixed order would even work for that specific
  instance.
- If asked to build genuine mixed-integer support, a joint LP relaxation (solved once per search
  node, over all variables simultaneously) is the real starting point, not a reordering of
  `BisectionConditioningSolver` and `BranchAndBoundSolver`.
