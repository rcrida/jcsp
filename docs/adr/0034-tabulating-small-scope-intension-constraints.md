# 0034. Compile small-scope intension constraints into tables

**Status**: Accepted

## Context

`KnightTour-06-int.xml.lzma` declares 36 variables over `0..35` and one `slide` over a single
binary intension — the knight's-move predicate:

```
or(and(eq(dist(div(%0,6),div(%1,6)),1), eq(dist(mod(%0,6),mod(%1,6)),2)),
   and(eq(dist(div(%0,6),div(%1,6)),2), eq(dist(mod(%0,6),mod(%1,6)),1)))
```

jcsp decomposed each of the 36 instances into two reified `AndConstraint`s under an
`AtLeastNConstraint`, with `div`/`mod` auxiliaries underneath: **180 variables, 534,935 nodes in 60
seconds, no solution.** Choco's own statistics on the same file:

```
== 38 variables ==            (36 real + 2 constants)
== 41 propagators ==
    PropLargeGACSTRPos #36    <- 36 table constraints, GAC via Simple Tabular Reduction
1 Solution, 0.521s, 10,017 Nodes, 19,874 Backtracks, 12 Restarts
```

Choco compiles each binary intension into a table of at most 36x36 = 1,296 tuples and propagates it
with GAC. The gap was never speed — it was **50x fewer nodes from a stronger model**.

An earlier investigation had profiled this same instance and concluded that variable views and a
dedicated `div`/`mod` propagator were not worth building, because the `div`/`mod` linking
constraints are only 1.7% of runtime (see
[ADR-0031](0031-domain-overlays-instead-of-whole-map-copies.md)). That profiling stands. What it
measured was where jcsp's time went, not why jcsp needed so much more of it; the decomposition's
*propagation strength* was the problem, and no amount of making the decomposition cheaper addresses
that.

## Decision

`TabulationRecognizer` enumerates a small-scope intension's Cartesian product, keeps the satisfying
tuples, and emits a `NaryTuplesConstraint` — which already has BitSet-indexed GAC
([ADR-0021](0021-bitset-indexed-gac-for-table-constraints.md)). It evaluates candidates through the
same `IntensionExpressionEvaluator` that `genericIntensionConstraint` uses, so a tabulated
constraint accepts exactly what the unpropagated fallback would have: tabulation changes how a
constraint propagates, never what it means.

**Position in the recognizer chain is the design.** The registry is ordered:

```
DistancePairComparison, SumOrLinear, BinaryRelation, GroundRelation, InSet,
NaryEquality, DistanceOfPair, Or(literalsOnly), BooleanProductChannel, Product,
  --> TabulationRecognizer <--
And, Or(full), RelationSum, Channel
```

Everything ahead of it produces a constraint that already propagates without auxiliary variables,
and keeps its cheaper table-free handling. Everything behind it reifies operands into fresh boolean
indicators, which is what tabulation exists to displace. `OrRecognizer` is registered **twice**,
once either side, because it does both: its two-literal `RelationLogicConstraint` fast path creates
no auxiliaries and must keep winning, while its `AtLeastNConstraint` decomposition must lose. A
single recognizer cannot be half ahead of another, so a `literalsOnly` flag splits it.
`AndRecognizer` needs no such split — it takes no handler and so cannot create indicators at all.

**Two ceilings, because one cannot bound an instance.** `MAX_INDEX_BITS` (1 MiB) caps a single
constraint's support index, which costs `sum(|domain|) * tuples` bits. `MAX_TOTAL_TUPLES` (500,000)
caps candidate tuples across a whole instance. Both are charged in candidate tuples, the unit the
check uses.

## Rejected alternatives

- **Variable views**, and a dedicated `div`/`mod` propagator. Both target variable *count*; the
  corpus measurement in ADR-0031 showed the variables they would remove are 1.7% of runtime. Views
  additionally cover under a tenth of the corpus's auxiliaries and none of `KnightTour-06-int`'s.
- **Capping on tuple count alone.** Ignores arity and domain width, and the support index — the
  thing that actually consumes memory — scales with neither.
- **A per-constraint cap only.** This was implemented first and **exhausted a 4GB heap parsing
  `RoomMate-sr0050-int`**. What separated the corpus's affordable case from its unaffordable one was
  never constraint size but constraint count: 36 constraints of 1,296 tuples against 4,900 of 2,401
  — barely twice the size each, 136 times as many. No per-constraint threshold can separate those.
- **Restricting tabulation to `and`/`or` node types.** Tried as a cheaper way to stop tabulation
  displacing the recognizers behind it. It protected the wrong ones: `MinVariableConstraint` and
  `ProductVariableConstraint` (clean, no auxiliaries) were the ones needing protection, while
  `ChannelRecognizer` and `RelationSumRecognizer` (which reify into indicators) should lose. It cost
  `QueenAttacking-06` 11 of its 46 tables and left it unsolved. Ordering, not a type filter, is the
  correct separator.
- **Representing an unsatisfiable constraint as an empty table.** A `NaryTuplesConstraint` with no
  tuples has an empty variable set and so sits outside the constraint graph entirely. Tabulation
  declines instead and lets the decomposition take it.

## Consequences

Corpus at a 30s budget: **72 -> 74 solved**, 0 failed, 0 `SolutionChecker` mismatches, and the only
two status changes are the gains.

| instance | before | after |
|---|---|---|
| `KnightTour-06-int` | 180 vars, UNKNOWN (534,935 nodes in 60s) | 36 vars, **SATISFIABLE in 0.26s, 303 nodes** |
| `QueenAttacking-06` | 269 vars, UNKNOWN | 48 vars, 46 tables, **OPTIMUM FOUND in 0.78s** |
| `RoomMate-sr0050-int` | 4,900 `RelationLogicConstraint` | unchanged |

jcsp now solves `KnightTour-06-int` in 303 nodes against Choco's 10,017, and in 0.26s against
Choco's 0.52s.

**`PredicateConstraint` becomes nearly unreachable for small-scope problems.** That is the point —
it propagates nothing — but it is a large behavioural shift, and it moved 46 parser tests whose
assertions described the old decomposition. Their semantic assertions were left untouched and still
pass, which is the evidence that tabulation preserves meaning. The decline paths those tests used to
cover are now exercised by direct recognizer unit tests
(`TabulationRecognizerTest`, `ChannelRecognizerTest`, `RelationSumRecognizerTest`) that construct a
handler and hand-built `XNode` trees rather than parsing XML, because these branches turn on what
`dispatch` returns — which an XML fixture controls only indirectly, if at all. Writing them that way
immediately found a real defect: `XNode#vars()` returns `null`, not an empty array, for a
constants-only tree, so any `and(eq(1,1),...)` would have thrown an NPE mid-parse.

`MAX_TOTAL_TUPLES` is currently unreachable on the bundled corpus — the `OrRecognizer` split alone
keeps `RoomMate-sr0050-int` away from tabulation. It is kept as the guard against an instance of the
same shape without that fast path, and is covered by a unit test that injects a three-tuple budget
rather than materializing half a million.
