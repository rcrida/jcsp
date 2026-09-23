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

**Status**: Accepted | Proposed | Rejected | Superseded by NNNN

## Context

What problem or constraint forced a decision here.

## Decision

What was actually decided.

## Rejected alternatives

What else was considered (or tried and reverted) and why it lost.

## Consequences

What this decision makes easy, what it makes hard, and what it obligates future changes to do.
```

Add the new ADR to the Index below in the same commit that adds the file — the index is the only
place the set is discoverable as a whole, and it has silently fallen several ADRs behind before.

**Rejected** is for an approach that was actually built and reverted, not one dismissed on paper
(that belongs in the winning ADR's Rejected alternatives section). Such an ADR is worth writing
precisely because the code is gone: without it the next person re-derives the same idea from the
same reasoning and re-does the work to find out it doesn't pay.

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
| [0014](0014-xcsp3-parser-via-callback-library.md) | XCSP3 instance parsing via a callback-driven library, not a hand-rolled parser | Accepted |
| [0015](0015-seeded-restart-tie-breaking-random-by-default.md) | Seeded per-restart tie-breaking, random by default | Accepted |
| [0016](0016-flow-based-gac-for-global-cardinality-constraint.md) | Flow-based GAC for GlobalCardinalityConstraint | Accepted |
| [0017](0017-range-based-gac-for-global-cardinality-constraint.md) | Range-based GAC for GlobalCardinalityConstraint | Accepted |
| [0018](0018-disjunctive-edge-finding-propagator.md) | Disjunctive edge-finding propagator | Accepted |
| [0019](0019-fixpointconsistency-per-object-dirty-tracking.md) | Per-object dirty tracking in FixpointConsistency | Accepted |
| [0020](0020-assignment-relaxation-for-gcc-linked-tables.md) | Assignment-style LP relaxation for GlobalCardinalityConstraint-linked tables | Implemented |
| [0021](0021-bitset-indexed-gac-for-table-constraints.md) | Bitset-indexed GAC for NaryTuplesConstraint | Implemented |
| [0022](0022-bitset-and-residue-arc-consistency-ac3bitrm.md) | Bitset+residue arc consistency (AC3bit+rm) | Accepted |
| [0023](0023-subset-sum-gac-for-linear-equality-constraints.md) | Subset-sum GAC for linear equality constraints | Accepted |
| [0024](0024-propagator-worklist.md) | Propagator worklist instead of a round-robin fixpoint | Accepted |
| [0025](0025-lp-model-reuse-across-search-nodes.md) | Reuse the LP model across search nodes by copying, not mutating | Accepted |
| [0026](0026-solution-guided-phase-saving.md) | Solution-guided phase saving, recorded from the deepest path only | Accepted |
| [0027](0027-xcsp3-circuit-is-a-sub-circuit.md) | XCSP3's `circuit` is a sub-circuit, not a Hamiltonian circuit | Accepted |
| [0028](0028-restart-on-solution-rejected-for-branch-and-bound.md) | Restart-on-solution for branch-and-bound, built and rejected | Rejected |
| [0029](0029-objective-cut-as-a-propagated-constraint.md) | Apply the incumbent as a propagated constraint, not only as a branch cut | Accepted |
| [0030](0030-nogood-learning-is-not-cdcl.md) | Nogood learning is not CDCL, and the gap explains why it does not pay | Accepted |
| [0031](0031-domain-overlays-instead-of-whole-map-copies.md) | Overlay the narrowings instead of copying the whole domain map; views and a div/mod propagator both target the wrong cost | Accepted |
