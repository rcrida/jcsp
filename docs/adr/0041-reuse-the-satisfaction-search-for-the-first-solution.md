# 0041. Reuse the satisfaction search to find branch-and-bound's first solution

**Status**: Accepted

## Context

`Taillard-js-015-15-0` is a 15×15 job shop that the optimization chain could not solve at all: 60s,
77,354 nodes, `restarts=0`, `s UNKNOWN`, and a remaining search space of **100.000000%** — not a
slow solve, a search that never escaped its first branching decision.

Stripping the objective and adding `e[i] <= 1330` (the variables' own declared upper bound, so the
same constraint set) turns it into a CSP, which routes to `DomWdegLubySearch` instead. It is then
easy, and how easy depends entirely on luck:

| seed | result | nodes | restarts |
|---|---|---|---|
| 7 | SATISFIABLE, 0.9s | 517 | 1 |
| 12345 | SATISFIABLE, 1.6s | 1,144 | 5 |
| 1 | SATISFIABLE, 3.9s | 3,238 | 14 |
| 99 | SATISFIABLE, 27.8s | 21,640 | **70** |

A 42× spread in node count, tracked by restart count: the textbook heavy-tailed runtime
distribution. The instance is trivial from a lucky first descent and hopeless from an unlucky one,
and the satisfaction chain needed **up to 70 abandoned attempts** before one paid off — each
abandoned after a 100–400-failure Luby budget.

Two explanations were ruled out by measurement before this one was accepted:

- **Node throughput.** Branch-and-bound ran at 1,284 nodes/s against the satisfaction chain's
  ~780–830. It is the *faster* of the two per node, and it explored 3.6× more nodes than the
  unluckiest satisfaction seed needed.
- **Variable ordering.** Since [ADR-0038](0038-injectable-variable-selector-factory.md)'s extension
  both chains use the same dom/wdeg selector, with the conflict-feeding hook sites lining up
  one-for-one.

The per-node LP relaxation was also measured and is a red herring here, though worth recording: on
this instance it covers **1 of 241 variables** (`$max`, the parser's `maximum`-objective auxiliary)
and builds **zero rows**, because none of the 256 constraints is one of the four types
`LpModelBuilder.isLinear` admits. Its bound is just `$max`'s propagated domain minimum, and `0.0` is
integral so it never even picks a fractional branching variable. It is a no-op solved 77,354 times —
a cost, but not the reason the search fails.

So the gap is not guidance and not speed. It is that `DomWdegLubySearch` can abandon a subtree and
`BranchAndBoundSolver` cannot.

## Decision

`BranchAndBoundSolver` takes an optional `firstSolutionSolver`, consulted once at the top of
`getSolutions` before its own search starts. Whatever that solver returns is validated, adopted as
the starting incumbent, and emitted as the stream's first element; the ordinary search then runs
unchanged underneath it.

`Solver.Factory` supplies the **satisfaction chain's own search** — the new shared
`Factory.satisfactionSearch`, which is everything below that chain's preprocessing:
`IndependentSubproblems → TreeDecomposition → CutsetConditioning → TreeSolver / DomWdegLubySearch`.
Reusing it rather than reimplementing a Luby ladder inside `BranchAndBoundSolver` is the decision,
and it is not just about avoiding duplication: it brings phase saving
([ADR-0026](0026-solution-guided-phase-saving.md)), the stagnation resets of
[ADR-0032](0032-adaptive-weight-reset-on-stagnant-restarts.md) and
[ADR-0039](0039-discard-the-phase-memory-on-stagnation.md), and structural decomposition, none of
which branch-and-bound has. The measurement above is of that whole stack, not of restarts alone, so
reproducing only the ladder would have been testing a different thing than the one that worked.

Preprocessing is deliberately excluded from the shared slice. Each chain has already run its own by
the time its terminal solver holds a problem, and the satisfaction chain's fixpoint snaps leftover
intervals to midpoints (`snap=hasContinuous`), which is the opposite of what the optimization chain
wants.

**Why the optimality proof survives.** The objection that killed budgeted restarts in the plan
behind [ADR-0028](0028-restart-on-solution-rejected-for-branch-and-bound.md) was that
`Xcsp3ProblemRunner` infers `OPTIMUM FOUND` from `!cancellation.isCancelled()` after the stream
drains, so a truncated restart would claim a proof it does not have. That objection does not reach
this design, because **nothing about the completeness-bearing search changes**. The injected solver
only supplies a starting bound; every improving solution after it still comes from the same
unrestarted, unbudgeted `search`, which is complete under that bound. A complete search that finds
nothing better than incumbent `C` proves `C` optimal, whatever found `C`. `getSolutions` keeps its
signature, its strictly-improving guarantee and its never-throws contract.

Three things are deliberately defensive:

- **`getSolution`, not `getSolutions`.** Only the former reaches `DomWdegLubySearch`'s restarts at
  all, which is the entire reason for delegating.
- **`LimitExceededException`/`SolverCancelledException` are caught.** The satisfaction chain's
  single-solution searches throw on truncation ([ADR-0011](0011-cancellation-token-for-main-chain-search.md))
  while this class has always truncated silently; seeding is not the place to change which contract
  `getSolutions` honours. On either, the search below simply starts unbounded.
- **The result is validated** with `isComplete && isConsistent` before being trusted, the same way
  `resolveContinuousResidual` validates its LP fill. A solution to the CSP is feasible for the
  optimization problem too — an objective is not a constraint — but that is a property of the
  injected solver, not of anything checked here, and an incumbent that is not actually feasible
  prunes real solutions away.

Withheld for problems with `BoundedDomain` variables, since the shared slice has no interval-snapping
fixpoint in front of it and a non-singleton `IntervalDomain` would reach a `DomainValuesOrderer` that
cannot enumerate one. Those problems keep branch-and-bound's own first descent.

`maxRestarts` is `DomWdegLubySearch.DEFAULT_MAX_RESTARTS` (512) here against the satisfaction
chain's own `Integer.MAX_VALUE`, so giving up hands control back instead of consuming the whole
budget. Honest caveat: at unit 100 those 512 restarts allow 230,500 inference failures in total,
roughly 3× what a 60s branch-and-bound run gets through, so **at competition time limits the cap
does not actually bind**. It bounds the phase in principle and is the place to tighten if a
measurement ever shows the first-solution search starving the optimization.

## Rejected alternatives

**A Luby failure budget reimplemented inside `BranchAndBoundSolver`.** This was built to the point
of compiling — a `FailureBudget` threaded through `search`/`searchCut`/`searchValues`, a
`BudgetExhausted` sentinel, a `restartRandomization` field, and a two-phase `getSolutions` — before
being replaced by the injected solver. It loses on three counts. It reproduces only the restart
ladder, not the phase saving, stagnation resets and decomposition that the measured 517–21,640-node
result actually came from. It needs a second copy of the Luby machinery, which then has to be kept
in step with the first. And it forces a re-descent: a budgeted phase that ends at the first solution
cannot hand its suspended lazy stream to an unbudgeted phase, so the path to that solution is
traversed twice. The injected solver has the same re-descent cost, but it buys the whole satisfaction
stack with it rather than a ladder.

**Restart-on-solution**, i.e. restarting after every improvement rather than only before the first
one. Built and rejected twice already ([ADR-0028](0028-restart-on-solution-rejected-for-branch-and-bound.md)).
Worth noting that neither of those measurements bears on this change: restart-on-solution fires
*after* an improving solution, and on `Taillard-js-015-15-0` branch-and-bound never finds one, so
the twice-rejected mechanism would never have fired here at all.

**Sharing the whole satisfaction chain, preprocessing included.** One line instead of a new helper,
but it re-runs `NodeConsistency` and the fixpoint the optimization chain has already run, with
`snap=true` — snapping continuous residuals to midpoints inside a search whose entire purpose is to
optimize over them.

## Consequences

`Statistics#restarts` is no longer always `0` on the optimization chain; it now counts the
first-solution search's restarts, and `nodesExplored` includes that search's nodes. Both are
measurements rather than contracts, but `CLAUDE.md` asserted the `restarts=0` structural fact and no
longer can.

`SolverListener.onRestart` gains a call site during optimization solves, where it has never fired.
An implementation keeps compiling; its event stream changes.

The first emitted solution is now typically *worse* than the one branch-and-bound would have found
itself, because a feasibility search has no reason to prefer a cheap solution — on
`Taillard-js-015-15-0` the seed is makespan **1330**, the loosest value the declared domains permit.
That is an improvement from nothing, but it also means the incumbent starts loose and
`phaseMemory.recordSolution` steers the search below toward a mediocre region. **The obvious next
step this opens up is to bound the first-solution search rather than leave it free** — a dichotomic
step asks "is there a solution with cost ≤ m", which is exactly the satisfaction problem this helper
now solves, so the machinery is already in place.

Two specific follow-ups, both surfaced by a unit test whose first draft asserted the opposite of what
turned out to be true:

- **Seeding is not free, and on a problem branch-and-bound already handles well it is a small loss.**
  A test asserting the seeded run explores fewer nodes failed at 75 against 70 on the `allDiff` over
  `{1..5}` fixture, where the unseeded search finds the optimum on its very first descent and the
  seed only adds work. The corpus says this is worth paying overall (+3 instances, none lost), but
  the cost is real and paid on every optimization solve.
- **Whether the seed belongs in the phase memory is an open question.** `accept` records it, so the
  search below replays a path chosen with no regard for cost — a likelier explanation for those five
  extra nodes than the re-descent itself. Setting the incumbent without recording the phase is a
  one-line change and has not been measured.

Finally, the first-solution search is reseeded randomly per `SolverConfig` like every other use of
`RestartRandomization`, so **which** first solution a solve gets is not reproducible unless a seed is
pinned. That made one existing runner test flaky rather than wrong — it asserts more than one `o`
line on a problem where one solution in six is optimal — and it now pins a seed.

A future caller can inject any `Solver` here, including a local search, without touching this class.

**It put the structural decomposers on the optimization chain for the first time, and that exposed
three real defects in them** — a clique enumeration that ignored `Cancellation`, enumeration work
discarded by a later clique's size check, and a cap scaled for memory rather than time. The first
showed up immediately as two instances running past a 60s limit until the harness killed them at
70s. They are fixed in [ADR-0042](0042-tree-decomposition-cost-control.md) rather than avoided by
narrowing what this injects, which was the first instinct and would have left a latent satisfaction-chain
bug in place. `AssignmentDomain`'s Javadoc asserted "tree decomposition is never reached from the
optimization chain" as a standing invariant; it is now reached, and that Javadoc is corrected to say
why the `BoundedDomain` reasoning it was supporting still holds (this change withholds itself from
any problem with a bounded domain).
