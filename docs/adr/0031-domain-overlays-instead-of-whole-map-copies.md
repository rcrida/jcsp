# 0031. Domain overlays instead of whole-map copies

**Status**: Accepted

## Context

`KnightTour-06-int.xml.lzma` builds 180 variables from 36 declared ones. That blow-up was the
motivating case for adopting Choco-style *variable views* — a derived variable with no storage of
its own, reading and writing through a bijection onto a base variable — which looked like the large
structural item that would fight the immutable `Map<Variable, Domain>` model directly.

Two measurements killed that framing.

**Which auxiliaries are view-able.** Only affine and boolean-channelling shapes can be views; that
is exactly Choco's own integer view set (`IntAffineView`, `BoolEqView`/`BoolNotView`, `RealView` —
there is no `div`, `mod`, `sub`, `add` or `dist` view). Bucketing every auxiliary
`Xcsp3CallbackHandler` creates across the bundled corpus:

| kind | count | view-able |
|---|---:|---|
| `$product` | 897 | no |
| reified indicators | 611 | no |
| `$divmod` | 320 | no |
| `$shift` | 182 | yes (offset) |
| `$dist` | 80 | no |
| `$const` / `$bool` | 25 | yes |
| `$max` / `$add` | 12 | no |

207 of 2127 auxiliaries (9.7%) are view-able, and 182 of those are 1-based index shifts. On
`KnightTour-06-int` specifically the 144 auxiliaries are **100% `$divmod`** — zero view-able.

**Where the time actually goes.** A 60s JFR profile of that instance (534,935 nodes, 5,066 samples)
attributed, inclusive:

| | share |
|---|---:|
| `AllDiffConstraint` | 16.9% |
| `AndConstraint` | 16.5% |
| `AC3` | 16.3% |
| `ReifiedConstraint` | 9.6% |
| `LinearVariableConstraint` (the div/mod links) | **1.7%** |
| `AbsoluteDifferenceConstraint` (the dist auxiliaries) | 0.9% |

So the auxiliary variables that dominate the model are a fiftieth of the runtime, and a dedicated
`div`/`mod` propagator — the narrower fix views suggested — would have targeted that 1.7%.

`AndConstraint.runFixpoint`, meanwhile, was the single largest jcsp frame at 15.5%, and its own time
was almost entirely `HashMap.putMapEntries` (254 samples), `putVal` (132) and `Map.ofEntries` (60).
It was copying, not propagating: `new HashMap<>(domains)` cloned the whole problem's domains on
**every** call to propagate conjuncts touching about six variables, then rescanned all 180 entries to
compute the diff, and on the infeasible path built a second full immutable copy that `propagate`
never reads (only `explainInfeasible` does).

## Decision

Add `DomainOverlay` (`consistency` package): a read-only `Map<Variable<?>, Domain<?>>` view over a
base map plus a small live map of narrowings, where `get` returns the narrowing when one exists and
falls through to the base otherwise.

`AndConstraint.runFixpoint` threads an overlay instead of a copy. The narrowings map *is* the diff,
so the whole-map rescan disappears, and `domainsAtFailure` becomes an overlay over a copy of the
narrowings (small) rather than of the merged domains (the whole problem).

Five explanation-path sites that were doing the same `new HashMap<>(domains); putAll(updated)` merge
purely to satisfy `Propagatable.allSingletonReason`'s single-map parameter now pass an overlay
instead: `DiffnPropagation#buildReason`, `CircuitPropagation#merged`, `CircuitConstraint`'s sub-tour
citation, and `InverseConstraint`'s two pruning passes. Those five already threaded `(domains,
updated)` and read through `updated.getOrDefault(v, domains.get(v))`; only the citation step forced
a merge.

This is safe as a drop-in because **no `Propagatable` implementation iterates the domains map it is
handed** — verified by enumerating every `entrySet`/`keySet`/`values`/`forEach` receiver under
`constraints/` and `consistency/`: each is a constraint's own field (`coefficients`, `forbidden`,
`literals`, `cardinalityRanges`, `targets`, `transitions`) or a local. Every access is
`domains.get(myVariable)`. `DomainOverlay#entrySet` is implemented anyway, materializing on demand,
so the view stays a correct `Map` for any future caller that does iterate.

## Rejected alternatives

- **Variable views.** Rejected on the two measurements above: under a tenth of auxiliaries are
  view-able, none of `KnightTour-06-int`'s are, and the variables they would remove are 1.7% of
  runtime. Separately, the design fights copy-on-write: a view needs a stable mutable identity to
  read through, which immutable domains deliberately remove, and a propagator that narrows a view by
  materializing a filtered set loses the transform structure, silently decoupling view from base.
- **A dedicated `DivModConstraint`.** Would halve `KnightTour-06-int`'s auxiliaries (144 → 72) and
  replace a generic `LinearVariableConstraint` link with a real propagator, but targets the same
  1.7%. Costing a variable count rather than a profile share is what made it look worthwhile.
- **Lazy whole-map copy**, the shape `DomainAccumulator` already uses (allocate the copy on first
  narrowing). Removes the copy only when nothing narrows; a fixpoint that does narrow still pays the
  full O(variables) clone, and conjuncts need a complete `Map` to propagate against, so the copy
  cannot simply be replaced by the small narrowings map. `DomainAccumulator` itself is left alone —
  it is lazy already, and its Javadoc records that an eager version measured as a 7-10% regression.

## Consequences

Measured by pinning a node budget (identical node counts mean identical logical work) and pairing
runs by `constraintChecks`, since AC3's per-JVM arc-ordering salt makes two launches of the same
seed take different paths. Across 40 runs, every work-class favours the overlay:

| instance | checks | base | new | speedup |
|---|---:|---:|---:|---:|
| `KnightTour-06-int` | 95,290 | 2819ms | 2290ms | 1.23x |
| `KnightTour-06-int` | 66,351 | 3272ms | 2854ms | 1.15x |
| `KnightTour-06-int` | 66,363 | 3313ms | 3086ms | 1.07x |
| `QueenAttacking-06` | 337,935 | 2813ms | 1993ms | 1.41x |
| `QueenAttacking-06` | 98,191 | 3505ms | 3026ms | 1.16x |

`Mario-easy-4` (1.00x) and `driverlogw-09` (0.985x) are neutral controls — neither has an
`AndConstraint` on its hot path.

The propagation contract is unchanged: same fixpoint, same diff *set*, so search is unaffected. A
node-count difference of 7 on `Mario-easy-4` was traced to the pre-existing per-JVM AC3 salt,
reproducible in the baseline against itself.

This obligates future `Propagatable` implementations to keep reading the domains map by key rather
than iterating it — the property that makes an overlay substitutable for a real map. Any propagator
that genuinely needs to iterate still works (`entrySet` materializes), but silently pays the copy the
overlay exists to avoid, so it should thread `(base, updates)` explicitly instead.
