# 0047. Declare caller-narrowed domains to the inference, rather than leaving them to wake nothing

**Status**: Accepted

## Context

[ADR-0029](0029-objective-cut-as-a-propagated-constraint.md) applies branch-and-bound's incumbent as
a real `LinearBoundConstraint` and propagates it into the domains at every node, for one reason: "a
predicate rejects one node; narrowing a domain is information every other propagator then compounds
with, through the ordinary fixpoint". On a job shop, bounding the makespan should tighten every
operation's latest start through the precedence chain.

That compounding never happened. `BranchAndBoundSolver.search` narrowed the parent node's domains
with the cut and handed the result to the child's `Inference`, and both halves of that inference seed
themselves from somewhere that cannot see the narrowing:

- `MAC` seeds its AC3 queue with `arcsInto(problem, variable)` — the arcs into the variable just
  branched on. The cut's variables are not among them.
- `Solver.Factory.propagationInference` then seeds the fixpoint
  ([ADR-0024](0024-propagator-worklist.md)'s worklist) with
  `changedVariables(problem, afterMac)` — a diff taken against the problem it was *given*, which is
  the already-cut one. The cut's narrowing is on both sides of that diff, so it cancels out.

A propagator is only woken by a variable in the dirty set, so the bound narrowed a domain and woke
nothing. `propagationInference`'s own Javadoc stated the invariant this broke verbatim: "`problem`
is exactly the parent search node's already-converged CSP, since nothing else touches it between
when the parent's own inference call finished and this one starts." Branch-and-bound was the thing
that touched it.

## Decision

A caller that narrows a problem's domains outside a propagation pass says so. `Inference` gains
seeded variants of both entry points,

```java
default Optional<ConstraintSatisfactionProblem> apply(problem, variable, assignment, Set<Variable<?>> alsoChanged)
default ConsistencyResult applyWithReason(problem, variable, assignment, Set<Variable<?>> alsoChanged)
```

whose `alsoChanged` names the variables the caller narrowed itself. `propagationInference` unions it
into the fixpoint's round-1 seed; the defaults ignore it, which is correct for an implementation that
re-derives everything regardless, and `Inference.withoutReasonTracking` forwards it so the default
(learning-off) configuration keeps it too.

`ObjectiveCut.narrow` returns an `ObjectiveCut.Narrowed` — the bounded problem plus the variables the
bound actually narrowed. That set is free: `Propagatable.propagate` already returns only the domains
it changed, so it is the result map's key set, wrapped rather than copied.
`BranchAndBoundSolver` threads it from the node that applied the cut down to `inferOrExplain`.

## Rejected alternatives

- **Pass a `null` seed (full first-round scan) at any node where the cut narrowed something.** Sound,
  and it is what the fixpoint did before ADR-0024's worklist, but it gives up precise dirty tracking
  at exactly the nodes where the bound bites — which on a tightly bounded search is most of them.
- **`enforce` the cut as a constraint at every node instead of narrowing** so the ordinary fixpoint
  re-derives it. This is what [ADR-0044](0044-bounded-probes-for-the-starting-incumbent.md)'s probes
  do, once per search. Per node it is the cost ADR-0029 already rejected: adding a constraint rebuilds
  the constraint graph, and `FixpointPropagation.Factory.forProblem` filters the propagator list on
  the constraint types present, so the inference would have to be rebuilt per node as well.
- **Diff the pre-cut and post-cut domain maps inside branch-and-bound** to recover the same set. A
  whole-map `changedVariables` pass per node, for a set the cut's own propagation result already
  holds.
- **Widen `Inference`'s existing signatures** rather than adding overloads. Every implementor (`MAC`,
  the per-solve inferences, and the lambdas throughout the tests) would have to change for a
  parameter almost all of them ignore.

## Consequences

- The invariant `propagationInference` documents is now one a caller can honour rather than one
  branch-and-bound silently violated: the problem it hands back is a fixpoint of its own propagators.
- An `Inference` implementation that seeds its propagation from a dirty set is obliged to honour
  `alsoChanged`. One that does not seed can ignore it, and the interface's defaults do.
- Any future caller that narrows domains between two propagation passes has a way to say so, and a
  reason to: the alternative is a narrowing that silently propagates no further.
- **It costs an optimality proof on this corpus.** Accepted with that known, not in ignorance of it.
  See the measurement below, which is the whole point of this section: anyone revisiting the
  cost/benefit should start from these numbers rather than re-deriving them.

## Measurement

`Xcsp3CompetitionRunner`, all 85 bundled instances, 60s each, fixed seed 20260830. The two dom/wdeg
arms were additionally run at seeds 7 and 99 over the 13 instances that differed between them; the two
MRV arms are one seed each. Four arms, varying only branch-and-bound's ordering and this decision; the
satisfaction chain and the incumbent seeder stay on dom/wdeg throughout.

| arm | optimum found | total solve |
|---|---|---|
| no seeding, dom/wdeg (before this ADR) | **23** of 31 | **773.9s** |
| seeding, dom/wdeg (this ADR) | 22 of 31 | 831.8s |
| seeding, MRV for branch-and-bound | 21 of 31 | 872.1s |
| no seeding, MRV for branch-and-bound | 20 of 31 | 913.9s |

84 of 85 solved in every arm, and no `SolutionChecker` cross-check failure anywhere. Four instances
account for every difference:

| instance | no seed / wdeg | **seed / wdeg** | seed / MRV | no seed / MRV |
|---|---|---|---|---|
| `Taillard-os-04-04-0` | OPT 17.9s, 112k nodes | **OPT 3.9s, 21k** | OPT 3.3s, 22k | SAT (timeout) |
| `PrizeCollecting-15-3-5-0` | OPT 4.7s, 11k | **SAT (timeout), 168k** | OPT 2.3s, 5k | OPT 2.3s, 6k |
| `GolombRuler-09-a4` | OPT 25.2s, 648k | **OPT 43.4s, 1,501k** | SAT (timeout) | SAT (timeout) |
| `GraphColoring-3-fullins-4` | OPT 25.3s, 121k | **OPT 24.7s, 121k** | SAT (timeout) | SAT (timeout) |

Everything else — the other 81 instances, every objective value, every satisfaction instance — is
unchanged, node-for-node. `PrizeCollecting` still *finds* its optimum of 20; what it loses is the
proof.

This is not seed luck. The no-seeding arm is bit-identical across all three seeds on these instances
(`PrizeCollecting` 11k/15k/12k, `GolombRuler-09-a4` 648k/643k/644k), the seeded arm varies only
slightly (168k/172k/178k, 1501k/1450k/1534k), and the sign is the same on every seed. Attribution is
equally direct: an arm built from this ADR's own commit with nothing changed but the seeding disabled
reproduces the no-seeding column exactly, which places all of it here rather than on any of the
fourteen other fixes in the same batch.

### What the regression is not

Two plausible mechanisms were built and refuted, both worth not re-deriving:

- **The LP's most-fractional branching hint.** Disabling it changes nothing: identical node counts
  with and without, on all three affected instances.
- **Bound-caused conflicts inflating dom/wdeg's weights.** Suppressing `onConflict` at every node
  whose domains the cut had narrowed recovers nothing (`PrizeCollecting` 173k → 160k, still no proof;
  `GolombRuler-09-a4` unchanged). Instrumentation shows why: only 14-23% of nodes have a non-empty
  cut narrowing, and the failing nodes are mostly not those.
- **Branching pulled onto objective variables** (the cut's narrowing only ever touches them). Measured
  at 0% objective-variable branches in both arms on `PrizeCollecting`.

### What it is

A feedback loop, with no seam to fix. Stronger propagation changes the tree; the tree changes which
conflicts occur (`PrizeCollecting`: 4k backtracks against 150k); the conflicts change dom/wdeg's
weights; the weights change the tree. The propagation itself is good in isolation — with MRV ordering,
seeding is 5k nodes against 6k on `PrizeCollecting`, and on `Taillard-os-04-04-0` it is the difference
between closing in 3.3s and not closing at all. Instrumentation shows the shape of the perturbation:
the mean domain size of the branched variable on `PrizeCollecting` goes from 1.63 (a chain of forced,
nearly-pinned decisions) to 8.31 (a genuinely bushier tree). And on `Taillard-os-04-04-0` the cut
narrows at exactly **one** node in either arm, yet the seeded search is 5x smaller — one node's extra
propagation cascading through everything below it.

### Rejected alternative, measured: MRV for branch-and-bound's ordering

Since the harm is specific to dom/wdeg's `domainSize / weightedDegree` and the propagation pays under
MRV on the two instances that motivated the question, MRV was tried as branch-and-bound's own selector
(the satisfaction chain left on dom/wdeg). It is worse: it recovers `PrizeCollecting` and loses both
`GolombRuler-09-a4` and `GraphColoring-3-fullins-4`, for 21 optima against 22, or 20 against 23
without seeding. [ADR-0038](0038-injectable-variable-selector-factory.md)'s choice of dom/wdeg
therefore survives contact with this change. A selector whose feedback loop does not punish stronger
propagation might get both, but plain MRV is not it.

### What would remove the need for this ADR

This decision patches a narrowing that happens *outside* the propagation loop, which is itself only
there because ADR-0029 judged a per-node constraint addition too expensive. Putting the bound in the
loop as an ordinary constraint would make the declaration unnecessary for this caller and close the
whole class of invisible mutation —
[ADR-0049](0049-layer-the-objective-bound-into-the-fixpoint.md) proposes how, and is explicit that it
is an architectural fix and not a fix for the search-order trade recorded above.

### A correction to ADR-0029

[ADR-0029](0029-objective-cut-as-a-propagated-constraint.md) measured its win and credited it to the
bound compounding through other propagators. That compounding never happened until this ADR, so
ADR-0029's measured benefit came from the narrowing's *direct* effects — the node's own candidate
enumeration and its consistency checks — and not from the mechanism its rationale describes.
