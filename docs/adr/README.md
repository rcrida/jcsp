# Architecture Decision Records

This directory records architecturally significant decisions for jcsp: ones that affect
cross-cutting structure, are hard to reverse, or were chosen over a real rejected alternative that
a future maintainer would otherwise have to rediscover (or re-litigate) from scratch.

## When to write one

Write a new ADR when a decision is:
- **Architecturally significant** — it affects structure that spans multiple classes/packages, a
  public API shape, or a dependency between subsystems.
- **Hard to reverse** — undoing it later means more than a local edit.
- **Contested or non-obvious** — a real alternative was considered and rejected for a specific
  reason, not just "the first thing that worked."

Don't write one for anything a class's own Javadoc already fully explains (mechanical
per-class/per-method behavior, why a specific algorithm cites the variables it does), or for a
decision that's cheap to revisit later. See the root `CLAUDE.md`'s documentation policy for the
full three-way split between Javadoc, `CLAUDE.md`, and this directory.

## Format

Each ADR is `NNNN-title-in-kebab-case.md`, numbered sequentially, using this template:

```markdown
# NNNN. Title

**Status**: Accepted | Proposed | Superseded by NNNN

## Context

What problem or constraint forced a decision here.

## Decision

What was actually decided.

## Rejected alternatives

What else was considered (or tried and reverted) and why it lost.

## Consequences

What this decision makes easy, what it makes hard, and what it obligates future changes to do.
```

An ADR is a record of a decision at the time it was made — later changes that revise the decision
get a new ADR that marks the old one **Superseded by NNNN**, rather than rewriting history in
place. A decision whose implementation later evolved without actually changing the decision itself
(e.g. a performance fix within the same design) can be noted in that same ADR's Consequences
section instead.

## Index

| # | Title | Status |
|---|-------|--------|
| [0001](0001-two-chain-decorator-solver-architecture.md) | Two-chain decorator-based solver architecture | Accepted |
| [0002](0002-nogood-learning-as-first-class-constraints.md) | Nogood learning as first-class propagatable constraints | Accepted |
| [0003](0003-race-competing-strategies-over-predictive-routing.md) | Race competing strategies instead of predictive routing | Accepted |
| [0004](0004-set-cp-as-a-parallel-stack.md) | Set-CP support as a parallel domain/constraint/solver stack | Accepted |
| [0005](0005-config-object-for-solver-configuration.md) | Config-object pattern for solver configuration | Accepted |
| [0006](0006-whitelist-based-domain-constraint-compatibility.md) | Whitelist-based domain/constraint compatibility validation | Accepted |
| [0007](0007-record-based-domain-object-model.md) | Record-based domain object model | Accepted |
| [0008](0008-decomposition-completeness-flag.md) | Decomposition-completeness flag for binary decompositions | Accepted |
| [0009](0009-joint-continuous-discrete-optimization.md) | Joint continuous/discrete optimization (LP relaxation) | Proposed |
| [0010](0010-push-listener-for-solve-progress.md) | Push-listener mechanism for solve progress | Accepted |
| [0011](0011-cancellation-token-for-main-chain-search.md) | Cancellation token for main-chain search | Accepted |
| [0012](0012-per-csp-propagator-filtering.md) | Per-CSP propagator filtering for the fixpoint loop | Accepted |
| [0013](0013-in-tree-jmh-benchmarks.md) | In-tree JMH benchmarks, not a separate module | Accepted |
