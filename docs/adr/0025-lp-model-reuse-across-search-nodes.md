# ADR-0025: Reuse the LP model across search nodes by copying, not mutating

**Status:** Accepted (2026-09-19)

## Context

`BranchAndBoundSolver` solves an LP relaxation at every search node when the objective is a
`LinearObjective` (ADR-0009). After the propagation work of ADR-0024 made the rest of the solver
substantially faster, that LP grew to **74% of a JFR profile** of `LowAutocorrelation-015`, up from
53% before. Within it, `LpModelBuilder.build` was 21% of the entire solve and ojAlgo's actual solving
53% — so a fifth of the run was spent *constructing* a model rather than solving one.

The first question was whether to solve the LP less often, which is how this was originally framed.
Measured with the LP disabled outright, at a 30s budget:

| Instance | LP on | LP off |
|---|---|---|
| `Knapsack-30-100-00` | OPTIMUM, 605 nodes | never proves optimality, 8,076,312 nodes |
| `Warehouse-opl` | OPTIMUM, 505 nodes | OPTIMUM, 233,098 nodes |
| `Cutstock-small` | OPTIMUM, 182 nodes | OPTIMUM, 474 nodes |
| `LowAutocorrelation-015` | 559,699 nodes | 2,854,428 nodes, still UNKNOWN |
| `Fastfood-ff10` | 225,526 nodes, o=728 | 1,027,009 nodes, o=728 |

The LP is decisive where it closes the problem and pure cost where it does not. Gating it uniformly
would be actively harmful, so the target became making it cheaper rather than rarer.

## Decision

Cache the structural model per solve and copy it per node.

`addRow` reads only a constraint's coefficients, operator and bound, and `relevantVariables`
collects from the same four linear constraint types — all structural. So the ojAlgo variables and
rows are fixed for a given constraint graph, and only the variables' own bounds move as domains
narrow. `LpModelBuilder#solve` gained a three-argument overload taking an identity token; the
template is built once and, per node, copied and re-bounded via `boundsOf`.

Two properties make the cache key right. It is stored in the CSP's per-`ConstraintGraph` auxiliary
cache, so learned nogoods — which change `getConstraints()`' reference on nearly every node but
contribute no rows, being non-linear — never invalidate it. And it is additionally keyed on a token
owned by one `BranchAndBoundSolver` instance, so two concurrent solves of the same problem cannot
share one mutable model.

Problems with assignment-relaxation rows (ADR-0020) opt out and rebuild per node: those rows add
fresh ojAlgo *variables* derived from each node's live domains, which cannot be carried between
nodes and would accumulate in a retained model.

## Consequences

Measured, fixed seed, interleaved A/B, three reps: **+8.7%** nodes on `LowAutocorrelation-015`,
**+6.9%** on `Vrp-A-n32-k5`, **+6.6%** on `Fastfood-ff10`. Instances that complete are unchanged in
both answer and node count — `Knapsack-30-100-00` 605 nodes/o=709, `Warehouse-opl` 505/o=383,
`Cutstock-small` 182/o=4, and their full improving-objective sequences are identical.

That is below the ~20% ceiling `build`'s profile share suggested, because `copy()` reclaims the
row-derivation work but still pays ojAlgo's object allocation. Worth recording as another instance of
a profile share overstating what is recoverable.

## Rejected alternatives

- **Mutating the retained model's bounds and re-solving it.** The obvious implementation, and wrong:
  ojAlgo retains presolve state on a model it has already solved, so subsequent solves return a valid
  but far weaker bound. Not a subtle degradation — `Knapsack-30-100-00` went from **605 nodes to
  288,022** while still reporting the correct optimum, which is exactly the shape of bug that passes
  a test suite unnoticed. Copying the template per node restores identical results. This is why the
  change is verified against *node counts and objective sequences*, not just answers.
- **Gating the LP by frequency.** See the table above: catastrophic on the instances where the LP is
  what closes the problem.
- **An adaptive gate** that backs off after N consecutive LP solves that failed to cut, resetting on
  any prune. Prototyped and measured: it left `Knapsack`/`Warehouse`/`Cutstock` byte-identical (the
  gate correctly never engages) and gained +64% nodes on `LowAutocorrelation-015`, +82% on
  `Vrp-A-n32-k5`. Rejected anyway, for now: the instances it helps stay UNKNOWN either way, so the
  gain is measured purely in node count, which is not an outcome measure for an unclosed optimization
  instance — and on `Fastfood-ff10`, the one non-closing instance that does report an objective, it
  returned the same 728. Three tuning constants with no principled basis, justified by no improvement
  in any answer. Revisit only with a real outcome measure: run the non-closing instances to a much
  longer budget and compare final objective values.
