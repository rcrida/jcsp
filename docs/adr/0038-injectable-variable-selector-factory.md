# 0038. Inject the variable ordering as a factory

**Status**: Accepted

## Context

`DomWdegLubySearch` built its own selector, twice:

```java
var selector = new DomWdegVariableSelector(csp.getConstraints());
```

so the satisfaction chain's variable ordering could not be changed without editing the solver. That
became a blocker rather than a wart while investigating `StripPacking-C1P1`
([ADR-0037](0037-energetic-reasoning-for-cumulative-constraints.md)), where propagation was made
roughly ten times stronger per node and the instance still went unsolved because dom/wdeg commits to
all 16 rectangle rotations before placing anything.

That ordering is not a tuning accident. `ratio` is `domainSize / wdeg`, and at the root the size
variables score `2/5 = 0.40` against the positions' `20/3 = 6.67`. Worse, `x[i]`'s constraints — its
`x + w <= 20` bound, the diffn and one projection — are a **strict subset** of `w[i]`'s, which adds
the rotation table and the second projection. Weights are incremented per constraint, so every unit
`x[i]` can ever gain also lands on `w[i]`: `wdeg(w) >= wdeg(x)` under any weighting whatsoever, and
with `dom(w) < dom(x)` the size variable wins at every node forever. No weighting scheme reaches
this, which means testing the hypothesis at all required a different selector, which required an
injection point.

## Decision

A new `AdaptiveVariableSelector extends UnassignedVariableSelector`, carrying a nested `Factory`
(`INSTANCE = DomWdegVariableSelector::new`, `createSelector(Set<Constraint>)`) and the four hooks a
selector needs to watch the search it is ordering. `SolverConfig#variableSelectorFactory` threads it
to `DomWdegLubySearch`, defaulting to dom/wdeg.

A **factory**, not a selector: an adaptive selector accumulates state about one search, so
`getSolutions` and `getSolution` must each build their own. This mirrors
`TreeUnassignedVariableSelector.Factory`, which `TreeSolver` already takes for the same reason.

A **separate interface**, not hooks added to `UnassignedVariableSelector`: the base interface is
implemented by `MinimumRemainingValuesSelector`, `ConflictedVariableSelector` and
`RandomVariableSelector`, none of which have any use for conflict notifications, and
`BranchAndBoundSolver`/`MinConflictsSolver` take one without ever raising an event. Hooks belong
with the selectors that want them. The hooks themselves default to ignoring their event, so a
selector that only ranks variables implements `select` alone — which is what an experiment needs.

The four hooks are named for the event rather than for dom/wdeg's response, since the whole purpose
is to admit a selector not built on constraint weights:

| was | is | raised when |
|---|---|---|
| `incrementWeights` | `onConflict` | inference wiped out a domain |
| `recordConflict` | `onValueRejected` | one candidate value was rejected, however detected |
| `reseedTieBreak` | `onRestart` | a restart is beginning |
| `resetWeights` | `onStagnation` | restarts have stopped reaching new ground |

The rename is one-for-one in signature. It is not one-for-one in *meaning* for the second row, and
that is the part worth recording: `recordConflict` fires at three sites, twice from the direct
consistency check and once from inference failure, and in every case one candidate value has been
rejected with the rest of the domain still to try. A first draft of this interface called it
`onRefutation` and documented it as "every value of `variable` was refuted, so the search is
backtracking past it" — which would have been a contract that lied to every future implementor.
Reading the call sites before naming them is what caught it.

## Consequences

An alternative ordering can now be supplied by a caller and measured against dom/wdeg on the same
problem, which is the immediate next step for `StripPacking-C1P1`: impact- or activity-based
selection, which ranks by the propagation an assignment actually causes and so is not subject to the
subset argument above.

Ordering cannot affect soundness or completeness — a complete search visits the same assignments
whatever order it chooses them in — so an injected selector can make a solve slower but never wrong.
That is stated on the interface, because it is the property that makes this knob safe to expose.

Four public methods on `DomWdegVariableSelector` are renamed. 3.1.0 is unreleased (Central has
3.0.0), so no published API breaks; ADR-0015 and ADR-0032 reference the old names and carry a
pointer here rather than being rewritten.

## Rejected alternatives

**Hooks on `UnassignedVariableSelector` itself, as no-op defaults.** One interface instead of two,
and `Factory` would sit with the thing it builds. Rejected because it puts four methods no plain
selector will ever implement onto the interface every plain selector does implement.

**A `Factory` returning the concrete `DomWdegVariableSelector`.** The smallest possible change, and
it keeps every name as it is. But an alternative heuristic would then have to extend
`DomWdegVariableSelector` and inherit weight machinery it does not use — inheritance for access
rather than for substitutability.

**Keeping the dom/wdeg method names on the new interface.** A much smaller diff, no doc churn. It
would have meant an impact-based selector implementing a method called `incrementWeights`, and would
have left the `recordConflict` mis-description in place unexamined.

## Extension (2026-10-02): wired into the optimization chain

As first built, `SolverConfig#variableSelectorFactory` reached only the satisfaction chain —
`Solver.java` had exactly one use site. `BranchAndBoundSolver` hardcoded
`MinimumRemainingValuesSelector` and called **none** of this interface's four hooks, against seven
call sites in `DomWdegLubySearch`. So setting the factory and then calling
`createSolver(csp, objective)` silently had no effect, and dom/wdeg would have degenerated to
dom/deg there even if injected, since nothing fed it conflicts.

`BranchAndBoundSolver` now takes `selectorFactory` in place of `unassignedVariableSelector`,
builds its selector per `getSolutions` call (adaptive selectors accumulate per-solve state, so two
solves from one solver must not share one), threads it through `search`/`searchCut`/`searchValues`,
and raises `onValueRejected` at the direct-consistency rejection and `onConflict` +
`onValueRejected` on inference failure — the same two points `DomWdegLubySearch` uses.
`MinimumRemainingValuesSelector` now implements this interface too, inheriting the no-op hooks, so
`constraints -> INSTANCE` is a valid factory for callers who want the old behaviour.

**The optimization chain's default selector therefore changes from MRV to dom/wdeg.** Corpus at
60s: **78 solved → 80**, zero failures, every new solution `SolutionChecker` OK.

| instance | before | after |
|---|---|---|
| `BinPacking-mdd-n1c1w4a` | UNKNOWN | **SATISFIABLE** |
| `BinPacking-tab-n1c1w4a` | UNKNOWN | **SATISFIABLE** |
| `Vrp-A-n32-k5` | UNKNOWN | **SATISFIABLE** |
| `Opd-07-007-003` | OPTIMUM, 22,172 nodes | OPTIMUM, **939** |
| `Fastfood-ff10` | OPTIMUM, 404,989 | OPTIMUM, **54,005** |
| `GolombRuler-09-a3` | OPTIMUM, 250,004 | OPTIMUM, **83,570** |
| `ChessboardColoration-07-07` | OPTIMUM, 902,098 | OPTIMUM, **346,161** |
| `Taillard-os-04-04-0` | SAT, best 195 | SAT, best **193** — the proven optimum |
| `PrizeCollecting-15-3-5-0` | OPTIMUM, 11,134 | OPTIMUM, 14,859 |
| `Warehouse-opl` | OPTIMUM, 506 | OPTIMUM, 784 |
| `GraphColoring-3-fullins-4` | SATISFIABLE, 177,295 | **UNKNOWN**, 22,578 |

The one lost instance is understood rather than mysterious, and it bounds where this heuristic pays.
`GraphColoring-3-fullins-4` is a COP over 405 variables and 3,524 constraints; dom/wdeg's `select`
computes a ratio per unassigned variable, each summing weights across that variable's constraints,
where MRV only reads domain sizes. Throughput fell ~7.8x and it no longer reaches a first solution.
The heuristic's own cost scales with the constraint graph, so a dense instance can pay more for the
guidance than the guidance saves — which is exactly the case a caller can now override, this ADR's
original purpose.
