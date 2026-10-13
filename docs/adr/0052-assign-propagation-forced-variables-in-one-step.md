# 0052. Assign propagation-forced variables in one step

**Status**: Accepted

## Context

After propagation a variable's domain is often already a single value, and the search used to spend a
node, a consistency check and an inference on assigning it. On `QuadraticAssignment-bur26a` 325 of the
351 variables are such functionally determined `d` variables (`D[x_i][x_j]` through a ternary table).
The plan this came from argued that, once [ADR-0051](0051-fail-driven-restarts-for-branch-and-bound.md)
made branch-and-bound re-descend from the root, every restart would re-pay that tail.

The measurements did not support that premise. Branch-and-bound's own search spends 975 nodes of
225,757 on `d` variables, and a bounded satisfaction probe spends none, because its dives die in the
`x` levels before the `d` tail is reached.

## Decision

`ForcedAssignment.extend(csp, assignment)` extends an assignment with the single value of every
unassigned singleton **discrete** variable, then checks the union of the constraints touching them in
one `isConsistentAmong` call. It returns the same assignment when nothing is forced, and an empty
result, counted as a backtrack, when the batch check fails. It is called at the top of
`DomWdegLubySearch#searchOne`, `DomWdegLubySearch#searchStream` and `BranchAndBoundSolver#search`,
before the completeness test, so a dive the extension completes is accepted as a solution.

- Sound because assigning a variable whose domain is already `{v}` cannot narrow anything: every
  `Propagatable` reads domains only. The per-variable steps could only have rejected earlier.
- Only `DiscreteDomain`s are extended; a singleton continuous or set-interval domain is left to the
  callers' own machinery.
- The touched constraints are collected by identity. A table constraint's `hashCode` is linear in its
  tuples, and hashing them per node took `Steiner3-08` from 1.6M nodes in 60s to 11k.

## Measurements

It is **not** a pure saving, and the plan's claim that it "defers nothing" does not hold: assigning the
forced variables early changes which constraints count as active in dom/wdeg's weighted-degree ratios,
so the search order changes.

On its own, on the pre-ADR-0051 solver: corpus solved 84 against 84, but `Taillard-os-04-04-0` lost its
optimality proof in the default-seed run and in one of three further seeds; bur26a's bounded probe ran
19% fewer `x` nodes in the same time; and objectives moved both ways at the timeout.

On top of ADR-0051 (growth 1.3), bur26a at 60s, three seeds:

| seed | ADR-0051 alone | with forced assignment |
|---|---|---|
| 20260830 | 2,229,501 (18 solutions) | 1,951,405 (36) |
| 1 | 1,938,533 (70) | 1,940,161 (57) |
| 2 | 2,389,840 (1) | 1,888,116 (59) |

Seed 2, which never improved under any growth factor, now does, and all three seeds clear the plan's
bar of 10% better with 20 improving solutions. Whole corpus: 84 solved and 23 `OPTIMUM FOUND`, the same
as the baseline and as ADR-0051 alone, with `Taillard-os-04-04-0`'s proof intact. Against ADR-0051
alone, `QueenAttacking-06` (7.8s → 3.5s), `Sat-flat200-00-clause` and `StripPacking-C1P1` are faster;
`driverlogw-09` (11s → 19s) and `qcp-15-120-00_X2` (2.1s → 5.7s) are slower; and two timed-out
objectives are worse: `TravellingSalesman-20-30-00` 104 → 111 and `Vrp-A-n32-k5` 1640 → 1850.

## Rejected alternatives

- **Scanning for singletons incrementally from the inference's changed set.** The brief suggested it to
  avoid an O(variables) scan per node. Not built: dom/wdeg's `select` already scans every variable per
  node with a heavier per-variable cost, and the measured overhead was dominated by the hashing above,
  not the scan.
- **Deferring the forced variables instead.** An earlier attempt to prefer declared variables over
  auxiliaries was built and reverted (25.7× more nodes, zero incumbents): branching on a determined
  variable is how propagation gets triggered.

## Consequences

- Node counts are no longer comparable across this change: a forced block is one search step but still
  counts one node per variable, because `Assignment#withValue` increments the counter. A listener
  census by variable-name prefix shows the forced variables as nodes.
- **A forced block runs no cancellation or limit check between its variables.** A dive that the
  extension completes can finish after `cancellation` fired. Any test that cancels from a node
  callback must do so early enough that the dive cannot be completed by forcing alone.
- A conflict among forced variables is caught by the batch check without any inference, so it records
  no nogood. Tests that relied on a pre-propagated singleton domain to provoke an inference failure
  now use domains that fail by a real wipeout.
- **Unresolved:** whether the two objective regressions above are noise or real was not measured; both
  instances time out and have been bimodal throughout. Re-run `TravellingSalesman-20-30-00` and
  `Vrp-A-n32-k5` on several seeds before reading anything into them.
