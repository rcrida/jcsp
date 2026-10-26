# 0059. Systems of binary linear equalities are solved jointly, by lattice reduction

**Status**: Proposed

## Context

`MarketSplit-01` (XCSP3 `MarketSplit` family: 30 0/1 variables, 4 equality knapsacks with
coefficients up to 99) is the Cornuéjols–Dawande market-split shape, `n = 10(m-1)` for `m = 4`. Each
row is a subset-sum, which [ADR-0023](0023-subset-sum-gac-for-linear-equality-constraints.md)
propagates well one row at a time, but the rows are only hard *together*, and the LP relaxation is
useless. Search enumerates a huge space of near-solutions.

The current solver is heavy-tailed on it, so no single time describes it. Seven seeds, 60s limit:

| seed | wall | restarts |
|---|---|---|
| 2 | 2s | 21 |
| 6 | 5s | 43 |
| 1 | 8s | 71 |
| 12345 | 21s | 164 |
| 5 | 26s | 251 |
| 4 | 45s | 305 |
| 3 | `UNKNOWN` at 60s | 406 |

Six of seven finish within 60s and three of seven within 20s. It is the distribution, not a typical
value, that a joint method has to beat.

A standalone prototype (not in the repository) applied the Aardal–Hurkens–Lenstra reformulation:
the solutions of `A x = d` are `x = x0 + B λ`, with `B` a basis of the kernel lattice, and LLL makes
`B` short. Every 0/1 vector is at squared distance exactly `n/4` from the point `(½,…,½)`, and no
other integer point is that close, so the solutions are the lattice points of the coset within that
radius. Enumerating them (Fincke–Pohst, nearest-first) on randomly generated market-split instances
(`n = 10(m-1)`, coefficients uniform in [0,99], either right-hand side `⌊Σ/2⌋` or planted from a
random 0/1 vector):

| instances | lattice prototype |
|---|---|
| `MarketSplit-01` (m=4) | about 30ms, unique solution |
| generated m=4, 6 instances | 18–22ms; 4 feasible, 2 proven infeasible |
| generated m=5, 6 instances | 87–373ms (1.3M–12M nodes) |
| generated m=6, 6 instances | 9.8s–43s (0.3B–1.4B nodes) |

The current solver was run on 8 of the generated instances with a single seed (12345) and returned
`UNKNOWN` at 60s on every one (m=4: 2, m=5: 4, m=6: 2). With the spread above, one seed per instance
shows that these instances are hard, not how often the current solver fails on them.

Five results (m=4 classic 1–3 and m=5 classic 1 and 3: four "no solution" and one "exactly one
solution") were cross-checked against an independent meet-in-the-middle count and agreed. m=6 was
not cross-checked.

The codebase already has the shape for this. [ADR-0055](0055-parity-tables-are-reasoned-about-jointly-as-a-gf2-system.md)
recognises a group of parity tables at the model level in `solver.redundant`, reasons about them
jointly, and posts the result as redundant constraints; `RedundantConstraints` runs before either
chain is built.

## Decision

**Recognise the system at the model level, solve it exactly once, and post the answer as ordinary
redundant constraints.** The originals stay, so a solution must still satisfy them and the caller's
problem is unchanged ([ADR-0050](0050-the-objective-cut-is-an-ordinary-constraint.md)).

- `LatticeSystems` groups `EQ` linear/sum constraints with integral coefficients and a constant
  integral bound that share variables, over variables with domain ⊆ {0,1}, using a union-find as
  `ParitySystems` does. A group is posted when it has at least two equations; one row is ADR-0023's
  subset-sum DP.
- `IntegerLattice` is an exact `BigInteger` LLL (Cohen's integral algorithm, δ = 3/4).
- `BinaryEqualitySystem` builds the embedding, reduces it, splits the reduced vectors into the
  kernel basis and the particular solution, and enumerates the coset within radius `n/4` of
  `(½,…,½)`. It returns `INFEASIBLE`, `SOLUTIONS(list)` or `INCONCLUSIVE`.
- The result becomes:
  - `SOLUTIONS` → an `NaryTuplesConstraint` over the group, capped at a few thousand tuples (more is
    `INCONCLUSIVE`). A unique solution fixes every variable at the root.
  - `INFEASIBLE` → an always-false unary constraint, which `NodeConsistency` turns into an empty
    domain.
  - `INCONCLUSIVE` → nothing is added.

**Never report infeasible on a cutoff.** The enumeration has a fixed node budget (about 32M, a second
or so) and checks `Cancellation` every 64k nodes. Either running out is `INCONCLUSIVE`, which means
the solver simply has less pruning, never a false `UNSATISFIABLE`
([ADR-0043](0043-inconclusive-is-not-unsatisfiable.md)). A budget in nodes is deterministic, so corpus
runs stay reproducible. `RedundantConstraints.withRedundantConstraints` takes the token from
`SolverConfig`, which it does not today.

**Soundness rests on three things.**
- Every leaf is verified against the original equations in `long` arithmetic, so a reported solution
  is genuine.
- Completeness (a refutation, or "these are all the solutions") rests on the pruning never cutting a
  branch it should keep. The prototype's enumeration uses double-precision Gram–Schmidt, so the
  radius and each level's interval get a safety margin. This is the part to review hardest, and a
  property test against brute force over seeded random systems is the check.
- If the reduced basis does not have the expected structure (kernel dimension wrong, no
  particular-solution vector), the result is `INCONCLUSIVE`, even where theory says infeasible over
  the integers, because the embedding weights are chosen conservatively rather than proven.

`Boolean` variables, `LinearBooleanBoundConstraint` and mixed groups are the same maths and follow
`ParitySystems`, which already accepts both kinds in one row. They are deferred until the integer path
passes its property test.

## Rejected alternatives

- **The λ-variable reformulation.** Add `n - m` integer variables `λ`, channel `x = x0 + B λ`, and
  branch on `λ`. It would leak 26–44 auxiliary variables into the returned `Assignment`, need a
  branching-priority hook, and put a solver concept into the problem model, which ADR-0050 rules out.
  The table-or-refute form does the lattice work once, up front, and needs none of that. It remains
  the likeliest fallback for a system with too many solutions to tabulate.
- **LLL on the equations' rows, posting the small combinations as redundant constraints.** Cheap and
  safe with other constraints present, but it only strengthens per-row propagation and does not
  replace joint enumeration; unmeasured, and expected to be weaker.
- **A native LLL (fplll) through JNI.** A platform-specific dependency for an algorithm that is a few
  dozen lines in exact arithmetic at the sizes in question.
- **A time budget.** Covers m=6 but makes outcomes depend on the machine. Left to a later phase,
  tied to `SolverLimits`.

## Consequences

If it works as prototyped, m=4 systems are decided at the root in tens of milliseconds and m=5 in
under half a second, replacing a search whose time ranges from seconds to over a minute depending on
the seed. m=6 needs 0.3B–1.4B nodes and falls back to ordinary search after paying at most the
budget, so the cost of a recognised group that cannot be decided is bounded at about a second per
solve.

Obligations: a property test of `BinaryEqualitySystem` against brute force (`2^n`, `n ≤ 20`) for
both feasible and infeasible systems and for the whole solution set; unit tests for `IntegerLattice`
(the reduced rows satisfy the size-reduction and Lovász conditions and span the same lattice); a
bundled-corpus sweep with `Xcsp3CompetitionRunner` showing no change in outcomes or node counts where
the pattern does not match; and a decision on whether `MarketSplit-01` and a generated m=4/m=5 pair
join the end-to-end examples.

Out of scope here: general bounded integer domains (a box inside a ball, with exact verification),
inequality rows through slack variables, objectives, `Boolean` variables, and a time-aware budget for
m ≥ 6. The status stays Proposed until the property test and the corpus sweep have run.
