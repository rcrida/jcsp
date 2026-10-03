# 0042. Make tree decomposition's clique enumeration cost-controlled

**Status**: Accepted

## Context

`TreeDecomposerImpl.decompose` builds, for each clique of the junction tree, an `AssignmentDomain`:
the cartesian product of that clique's variable domains, filtered to the combinations consistent
with the whole CSP. `TreeDecompositionSolver` bounds each clique's combination count by
`maxDomainSize = min(d^targetTreewidth, MAX_DOMAIN_SIZE_CAP)`, and that cap was `1_000_000`.

Three defects, found while measuring something else (see
[ADR-0041](0041-reuse-the-satisfaction-search-for-the-first-solution.md), which put this code on the
optimization chain for the first time and so exposed them):

**1. The enumeration ignored `Cancellation`.** `grep -c cancellation AssignmentDomain.java` returned
0. The cap makes the loop finite, not short: a million combinations each costing a full
`Assignment.isConsistent` pass over every constraint is far more than any caller's time limit.
`Tpp-3-3-20-1` under a 10s limit ran until the harness killed it at 70s, with the stack inside
`AssignmentDomain.populateCombinations` in all six samples taken.

**2. Size-checking and enumerating were interleaved.** The loop checked clique *i*'s product against
the cap, enumerated clique *i*, then moved to *i+1* — so a clique that busted the cap discarded every
enumeration already completed for the cliques before it. The decision is all-or-nothing and the
products are `BigInteger` multiplications that enumerate nothing, so there was never a reason to pay
for any enumeration before taking it. `QuadraticAssignment-bur26a` spent **74.38s** enumerating and
then returned `Optional.empty()` because of a later clique.

**3. The cap was a memory bound used where time dominates.** The existing Javadoc says so in as many
words — *"within the same memory budget"*. A million `Assignment`s in a `HashSet` is a defensible
memory figure; a million consistency passes per clique, times the number of cliques, is not a
defensible time figure. `Tpp-3-3-20-1` is the proof: every clique fits under 1,000,000, so the cap
admitted the decomposition, and enumerating it then consumed the entire 60s budget. That instance is
`OPTIMUM FOUND in 0.64s` when the decomposition is declined instead.

## Decision

All three fixed together, since each alone leaves the pathology reachable.

**`AssignmentDomain.of(variableDomains, csp, cancellation)`** returns `Optional<AssignmentDomain>`,
abandoning the enumeration via `takeWhile(a -> !cancellation.isCancelled())` — once per combination
produced, negligible beside the consistency pass that follows it. The existing
`AssignmentDomain(Map, csp)` constructor stays, delegating with `Cancellation.NEVER`, so there is one
enumeration implementation rather than two. A cancellation arriving only after the enumeration
finished also yields empty: a complete domain is no use to a search that is stopping.

`TreeDecomposerImpl` takes the `Cancellation` as a **constructor field** rather than a `decompose`
parameter, matching how every solver in the chain takes one, and leaving `TreeDecomposer`'s interface
untouched.

Cancellation surfaces as `Optional.empty()` from `decompose` — "decomposition declined" — **not** as a
thrown `SolverCancelledException`. `decompose` is reached from `TreeDecompositionSolver.getSolutions`
as well as `getSolution`, and the stream contract truncates silently
([ADR-0011](0011-cancellation-token-for-main-chain-search.md)). Declining is also the graceful
answer: the caller falls through to `inner`, which checks cancellation at its own first node and
stops immediately.

**Every clique's size is checked before any clique is enumerated**, in two passes over one
pre-computed list of clique domain maps.

**`MAX_DOMAIN_SIZE_CAP` drops from 1,000,000 to 10,000.**

## Rejected alternatives

**Keeping the cap and relying on cancellation alone.** Cancellation turns a hang into a wasted
budget, which is strictly better but still a loss: `Tpp-3-3-20-1` becomes `s UNKNOWN` at 60s instead
of running over, where declining the decomposition gets `OPTIMUM FOUND` in 0.64s. Interruptibility is
a safety property, not a cost-control one.

**A work counter instead of a size cap** — abandon after N combinations summed across all cliques,
which is what actually correlates with time. More directly aimed at the real cost than a per-clique
size bound, and worth revisiting, but it introduces a second budgeting concept next to the existing
one and the measurement below did not need it.

**Making the enumeration incremental**, filtering partial assignments by consistency as each
variable is added rather than only complete combinations. Sound — `isConsistentAmong` skips
constraints whose variables are not all assigned, so a consistent combination has consistent
prefixes — and probably a large speedup, since it prunes the product rather than generating it. Left
alone deliberately: it changes what the enumeration computes on the way, and this ADR is about not
paying for enumerations that are then discarded.

## Consequences

Decompositions with a clique between 10,000 and 1,000,000 combinations are now declined, and those
problems fall through to `CutsetConditioning`/`DomWdegLubySearch`. That is the intended trade and
it is a real behaviour change on the satisfaction chain, not only on the path ADR-0041 added.

`AssignmentDomain` now depends on `solver.Cancellation`, which `assignments.Assignment` and
`assignments.SolverLimits` already do, so the layering is unchanged in kind.

`Cancellation`'s class Javadoc enumerates its check sites; the clique enumeration joins that list.
Future additions should keep it accurate — it is the only place the set is written down.
