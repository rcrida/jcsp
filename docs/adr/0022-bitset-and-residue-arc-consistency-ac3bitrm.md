# 0022. Bitset+residue arc consistency (AC3bit+rm)

**Status**: Accepted — implemented, benchmarked, **not wired into any default chain**

## Context

`AC3.revise()` is a nested loop calling `BinaryConstraint#isSatisfiedByArcValues` once per (from-value,
to-value) pair — `O(|D_i|*|D_j|)` per arc revision, dominated by the virtual-call/comparison cost, not
domain size alone. It's already the most expensive single propagator in `FixpointPropagation.PROPAGATORS`
(placed last there for exactly that reason), and, via `MAC`, it's the arc-consistency step run once per
assigned variable at *every* search node.

Lecoutre & Vion's AC3bit+rm ("Enforcing Arc Consistency using Bitwise Operations") replaces the nested
support-search with:
- **bit**: a precomputed `BitSet` per (arc, constraint, from-value) recording which to-side values are
  compatible, checked against the to-side's *current* live values (also a `BitSet`) via word-parallel
  `BitSet#intersects` — the same category of win as ADR-0021's bitset index for `NaryTuplesConstraint`.
- **rm**: multidirectional residues — the last value found to support a given (arc, constraint,
  from-value) is tried first, and a support found in one direction is also recorded for the reverse arc
  (sound, since `isSatisfiedByArcValues` is the same underlying relation regardless of which `Arc`
  direction frames the call).

Two design questions were resolved before implementation, and both were later revisited once real
evidence existed — see Decision:
- **Routing between `AC3` and the new class**: initially, no threshold — prefer the new class wherever
  applicable, deferring any domain-size-based routing until real benchmark numbers existed. This mirrored
  `project_jcsp_backtracking_vs_domwdeg_routing` and ADR-0003 (predictive routing heuristics falsified by
  benchmarking, twice already, in this codebase).
- **Residue scope**: initially per-call only, rebuilt fresh inside each `applyQueue`/
  `applyQueueWithReason`/`revise(csp, arc)` invocation, never persisted on the shared constraint graph —
  to sidestep the concurrency hazard ADR-0021 identified for `RaceLocalSolver`'s parallel attempts sharing
  constraint objects. This turned out to be over-cautious; see Decision.

## Decision

Added `io.github.rcrida.jcsp.consistency.arc.AC3BitRm`, a second `ConstraintConsistency` singleton
alongside `AC3` (not a replacement — `AC3` remains the simpler, independently-tested reference
implementation, the same relationship `BacktrackingSearch` has to `DomWdegLubySearch`). **After
benchmarking (see below), `AC3BitRm` is not wired into any default chain** — `MAC`,
`FixpointPropagation.PROPAGATORS`, `TreeSolver`, and `LocalSolver.Factory#PREPROCESSORS` all use `AC3`
directly, exactly as before this ADR. `AC3BitRm` remains available as a documented, tested, benchmarked
alternative for a caller who knows their constraints have genuinely expensive per-pair checks (see
Consequences) — wire it in the way `AC3BitRmBenchmark#buildChain` does, substituting it for `AC3` at both
the `MAC`-equivalent and `FixpointPropagation.PROPAGATORS` call sites.

### A real correctness fix along the way: `ConstraintGraph#declaredDomains`

A domain-content-dependent cache (the per-variable value→bit-index mapping) isn't automatically safe to
build from "whichever domains are live the first time this class touches a graph", the way `AC3`'s own
purely-structural `arcIndex` cache is. `MAC#apply` narrows its target variable to a singleton via
`problem.withDomain(...)` *before* ever calling into arc consistency. In the real `Solver.Factory` chain
this was harmless — `PropagationFixpointSolver` already populated the cache from the full, pre-search
domains during preprocessing, before `MAC` ever ran. But several existing tests (e.g.
`DomWdegLubySearchTest`) construct `DomWdegLubySearch`/`MAC` directly, bypassing that preprocessing pass
entirely. There, `MAC`'s first call *was* the cache's first build, and by then the just-assigned
variable's domain had already been narrowed to one value — a sibling search branch trying a *different*
value for that same variable later hit a value the cache never indexed. Confirmed as a real, reachable
failure (`AssertionError`/`NullPointerException` from several existing tests), not a hypothetical.

Fixed at the root rather than patched around: `ConstraintGraph` gained a `declaredDomains` field,
captured once, only when a *fresh* graph is actually constructed (never on reuse via
`toBuilder()`/`withDomain`/`withDomains`, which pass an existing graph straight through). Since a
constraint graph's identity is created exactly once per structurally-distinct problem and shared for that
problem's whole lineage, and every propagator only ever narrows a domain, `declaredDomains` is a provably
safe closed superset of every value any of that graph's variables will ever hold — regardless of which
decorator or call pattern happens to touch the graph first. Exposed publicly via
`ConstraintSatisfactionProblem#getDeclaredDomains()`. `AC3BitRm`'s value-index cache is built from
`getDeclaredDomains()`, not `getVariableDomains()` — making the invariant true by construction.

### The optimization journey

Initial JMH benchmarking (see `io.github.rcrida.jcsp.benchmark.AC3BitRmBenchmark`, same node-budget/
seeded-`RestartRandomization` discipline as `CsplibBenchmarks#magicSquareXcsp3LargeNodeBudget`) found the
first working implementation up to **46% slower** than `AC3` on a dense, narrow-domain binary CSP — the
opposite of the intended win. Profiling (JFR, execution samples + allocation samples) drove four real
fixes, each independently confirmed to help before moving to the next:

1. **`Map.copyOf` → plain `HashMap`** for the per-variable value-index (`ValueIndex#indexOf`, looked up
   once per domain value on every revise call). `Map.copyOf`'s immutable-map lookup
   (`java.util.ImmutableCollections$MapN#probe`) was ~9% of total CPU samples with no equivalent cost in
   `AC3`'s own profile. ~5% wall-clock win.
2. **Removed `BitSet#clone()` and eager reverse-`Arc` allocation.** The wipeout check now uses
   `BitSet#intersects` (no allocation) instead of `clone()`+`and()`+`isEmpty()`; when support does exist,
   `support[i]`'s own set bits are scanned directly against the live-bits set rather than materialising an
   intersection. The reverse arc/residue lookup, needed only when a *fresh* support is found, moved from
   unconditional (every call) to computed once per call, lazily.
3. **Persisted, shared residues** — reversing the original "per-call only" decision. The residue
   infrastructure itself (`Map<Arc, int[][]>`, rebuilt via `computeIfAbsent` on every single call) turned
   out to be a real, measurable cost (~11% of CPU time: `computeIfAbsent`/`hash`/`resize`/`putVal`), and
   rarely paid for itself given typically-short per-node propagation chains. Re-examined the thread-safety
   argument that motivated the original per-call scope, and found it didn't actually apply here: a residue
   slot only ever holds either its initial `-1` or a value `y` found by scanning `support[i]`'s own
   (immutable, once-built) bitset, so "`y` supports index `i`" is a permanent structural fact independent
   of which thread or search branch wrote it. The read side never trusts that fact alone — every use
   re-checks `liveJ.get(cachedY)` against the *reading* call's own current domain, so a stale or
   racily-overwritten residue is simply treated as a miss (identical cost to never having cached it), never
   an incorrect deletion. `int` array-element reads/writes are also always atomic per the JLS (no tearing),
   so concurrent unsynchronized access can produce staleness but never a corrupted value. This is a
   genuinely different shape of state to ADR-0021's own concern for `NaryTuplesConstraint` (a
   backtracking-coupled, order-dependent "current tuple list" needing real undo-on-backtrack) — the
   original decision had over-applied that precedent without checking whether this specific cache shared
   the same vulnerability. Residues are now built once per graph (all `-1`) alongside `supports`, shared
   and mutated for the rest of the graph's lifetime. ~13% further wall-clock win.
4. **`IdentityHashMap`** for the value-index, support, residue, and reverse-arc maps, replacing the
   remaining plain `HashMap`s (`HashMap#getNode` was ~20% of CPU samples post-fix-3). Verified sound before
   implementing, not just assumed: checked `IntRangeDomain`'s and the shared `DiscreteDomain`/
   `NumericDiscreteDomain` builders' `toBuilder().delete(...).build()` narrowing path directly — every
   builder starts from `mutableValues.addAll(initial)` (copies references, never reconstructs elements)
   and `delete()` only removes, so a retained value keeps its exact original object reference all the way
   back to `declaredDomains`. Since domains only ever narrow via this exact pattern throughout the
   codebase, value-object identity is preserved for a variable's whole lineage, making `IdentityHashMap`
   sound for domain-value keys, not just `Arc`/`Variable` keys.

   This surfaced a **real, separate correctness bug**, caught by the existing test suite before it shipped:
   `BinaryConstraint#getArcs()` constructs a fresh `Arc.of(...)` on every call, so two `Arc` instances for
   the same (from, to) pair are `.equals()` but not `==`. `IdentityHashMap`-keyed lookups using a
   caller-supplied `Arc` (the public `revise(csp, arc)` entry point, and — critically — `MAC`'s own queue,
   built from its own fresh `getArcs()` stream, the actual production path) would silently return nothing,
   dropping all constraints with zero narrowing and no error. Fixed by deriving `allArcs` from
   `arcConstraints.keySet()` instead of a second `getArcs()` pass (guaranteeing one canonical `Arc`
   instance per pair) and adding an explicit `canonicalArcs` (plain, equality-based) translation at the
   two points a caller-supplied `Arc` can enter: once per queue, up front, in `applyQueue`/
   `applyQueueWithReason` (not per `poll()` — tried first, and shown by benchmarking to cost more than it
   saved, since the large majority of polls are already-canonical internally-requeued arcs), and once in
   the public single-arc `revise(csp, arc)`. ~7% further wall-clock win once the over-eager per-poll version
   was corrected to the once-per-queue version.

### Benchmark results and the final wiring decision

Six scenarios (`AC3BitRmBenchmark`, JMH `avgt`, `AC3` vs `AC3BitRm`, after all four fixes above):

| Scenario | AC3 | AC3BitRm | Result |
|---|---|---|---|
| Dense binary, narrow domain (d=6) | 777.3ms | 955.2ms | AC3BitRm ~23% slower |
| Dense binary, medium domain (d=25) | 19.0ms | 21.6ms | AC3BitRm ~14% slower |
| Dense binary, wide domain (d=60, one `BitSet` word) | 145.0ms | 144.4ms | ~parity |
| Dense binary, very wide domain (d=250, four `BitSet` words) | 1716.8ms | 1731.1ms | ~parity (still no win) |
| Real XCSP3 instance (`RoomMate-sr0050-int`, 4900 constraints) | 184.3ms | 195.5ms | AC3BitRm ~6% slower |
| Synthetic expensive per-pair check (`Blackhole.consumeCPU(50)`) | 23.0ms | 22.3ms | AC3BitRm ~3% faster |

All scenarios except the last use `biPredicateConstraint` with a cheap array-lookup lambda — representative
of every real constraint type in this codebase (comparators, table lookups, simple predicates). The
domain-width hypothesis (word-parallelism should win for wide domains) did **not** hold even at 250 values
(four `BitSet` words): `AC3BitRm`'s fixed per-call overhead (multiple map lookups, extra indirection
layers even after fix 4) keeps pace with whatever word-parallel gain exists, as long as the thing being
replaced is still just a cheap comparison. The one scenario `AC3BitRm` wins is when
`isSatisfiedByArcValues` itself does genuinely expensive work — there it pays that cost once per pair at
table-build time instead of on every revise call, and wins for exactly the reason the literature predicts.

Since every real constraint type in this codebase evaluates cheaply, wiring `AC3BitRm` in as the default
would regress the common case to chase a win that doesn't exist for this codebase's actual workloads. The
wiring was reverted to `AC3` everywhere it had been changed.

## Rejected alternatives

- **A fallback-on-mismatch guard** for the `declaredDomains` cache-safety issue (check whether a value is
  missing from the cached index at revise time, fall back to `AC3.revise` for that call) — considered
  before settling on the `declaredDomains` fix. Rejected: it would silently degrade to zero speedup for
  any graph whose first touch happens to be narrow, with no signal that happened. The `declaredDomains`
  fix delivers a correct index in every call pattern instead of only the one that happens to preprocess
  first.
- **Keeping residues per-call** (the original design) once the persisted version was shown both sound and
  faster — superseded within this same ADR rather than written up as a separate one, since the underlying
  decision ("add `AC3BitRm`") never changed, only this sub-decision within it. See the optimization
  journey above for the reasoning reversal.
- **A domain-size-based routing threshold between `AC3` and `AC3BitRm`.** Considered, but the benchmark
  data doesn't support domain size as the right axis at all (see the very-wide-domain result above) — the
  real signal is per-pair constraint check cost, which has no cheap, automatic way to measure. No router
  built; left as a manual choice for a caller who knows their own constraints are expensive to evaluate.
- **Wiring `AC3BitRm` into `TreeSolver`/`LocalSolver.PREPROCESSORS` too**, for uniformity. Moot once the
  benchmark data showed no default-wiring win at all for typical constraints.

## Consequences

- `assert`-based null-check guards were tried first for an (unreachable-by-construction) "value not in
  cached index" case, but JaCoCo's built-in assert-elimination filter doesn't recognise a null-check
  assert shape the way it does a plain boolean condition (confirmed empirically: `Arc`'s `assert from !=
  to` shows full branch coverage, `assert i != null` did not). Replaced with plain `int i = index.get(x);`
  relying on Java's own auto-unboxing `NullPointerException` if the invariant is ever violated.
- Verified via the full test suite (100% instruction/branch coverage, Javadoc `{@link}` check), the
  bundled XCSP3 competition corpus (`Xcsp3CompetitionRunner`, default corpus/budget, zero `SolutionChecker`
  mismatches both before and after the wiring reversion), and `AC3BitRmBenchmark` (JMH, six scenarios,
  documented above).
- `AC3BitRm` is fully implemented, independently tested (`AC3BitRmTest`, mirroring `AC3Test` plus
  bit+rm-specific coverage for multi-constraint arcs, residue hit/miss/scan-skip, and canonical-`Arc`
  translation), and benchmarked, but inert unless a caller wires it in manually.

## Future work

- **A real router**, if a reliable, cheap way to estimate a constraint's per-pair evaluation cost is ever
  found (e.g. a constraint self-reporting "expensive" at construction time). No such mechanism exists
  today, and none is proposed here — the benchmark data only rules domain size out as a proxy, it doesn't
  supply a working alternative.
- **Extending `AC3BitRm` to `TreeSolver`/`LocalSolver.PREPROCESSORS`** if a corpus instance or benchmark
  ever shows a genuinely expensive constraint check dominates one of those call patterns specifically.
- **Persisted, thread-confined residues with real cross-node incrementality** beyond what the current
  shared/self-verifying design gives — would need the same kind of larger architectural change ADR-0021's
  own Future Work already scoped and declined to attempt (propagator instances with real,
  search-thread-confined state).
