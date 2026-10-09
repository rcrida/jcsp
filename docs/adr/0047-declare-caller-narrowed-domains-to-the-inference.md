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
- Not measured over the corpus. This is a correctness fix to a mechanism whose whole purpose is
  propagation strength, so the direction is not in doubt, but the per-node cost of waking the
  objective's propagators is real and the net effect on the corpus is unmeasured.
