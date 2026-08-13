# 0014. XCSP3 instance parsing via a callback-driven library, not a hand-rolled parser

**Status**: Accepted

## Context

jcsp already implements almost every CSPLib-style global constraint, but every CSPLib instance in
the repo is hand-transcribed into Java source (`ProbNNN*Test` classes under
`solver.examples.csplib`). XCSP3 is the standard machine-readable instance format for the CP
competition and covers almost exactly jcsp's existing constraint vocabulary, so a parser lets jcsp
consume external instances directly instead of manual transcription.

XCSP3's own grammar is large: an XML schema for variables/constraints/objectives, plus a
functional expression sub-grammar for `<intension>` (prefix-notation arithmetic/relational/boolean
trees like `eq(add(x,y),10)`). Reimplementing both from scratch was the natural-seeming first
option, but would dwarf the actual jcsp-specific work — mapping each parsed construct onto jcsp's
existing `ConstraintSatisfactionProblemBuilder` methods.

## Decision

Depend on `org.xcsp:xcsp3-tools` (Maven Central, `2.5`; excludes its transitive `junit:junit` via
`<exclusions>` so a JUnit 4 test framework isn't leaked as a compile-scope dependency of a library
whose own tests are JUnit 5). It does the XML parsing and expression-tree parsing itself;
`Xcsp3CallbackHandler` (`io.github.rcrida.jcsp.parser.xcsp3`) implements its `XCallbacks2`
interface, receiving one callback per XCSP3 construct and translating each directly into a call on
`ConstraintSatisfactionProblemBuilder` — `buildCtrAllDifferent` → `allDiffConstraint`,
`buildCtrSum` → `sumConstraint`/`linearConstraint`, etc. `Implem.rawParameters()` is called in the
handler's constructor to disable the library's own constraint "recognition" (which by default
tries to reroute simple shapes like `eq(x,2)` into more specific callbacks such as
`buildCtrPrimitive`/`buildCtrExactly`/`buildCtrAmong`) so every constraint always arrives through
the one generic callback per constraint type this class overrides, rather than needing to
implement dozens of recognized-primitive variants for the same semantic content.

`<intension>` arrives already parsed into an `XNodeParent`/`XNodeLeaf` AST (the library's own
expression-tree representation); `IntensionExpressionEvaluator` is a small recursive walker
converting that AST into a `Predicate<Assignment>` (for `predicateConstraint`) or, historically,
a numeric evaluator for objectives (removed — see Consequences). It covers the
arithmetic/relational/boolean operators exercised by this project's XCSP3 test fixtures
(`dist` included), plus `in`/`notin` against a literal `set(...)` of constants — the latter needed
a small carve-out in the otherwise-uniform per-node evaluation loop, since a `set(...)` node
doesn't reduce to a single scalar the way every other operand does, so `IN`/`NOTIN` are recognized
and their right operand read as a value list before the generic recursion ever reaches it. Not the
full expression language (no `min`/`max`/`xor`/`iff`, `subset`/`supset`, or a `set(...)` containing
anything other than constants) — an unrecognized operator throws
`UnsupportedXcsp3ConstraintException` rather than mis-evaluating silently.

Scope is a narrow MVP: variable domains, `intension`, `extension` (n-ary positive/support tables
plus the unary single-variable form, both positive and negative), `allDifferent` (plain list and
`matrix`, decomposed into one `allDiffConstraint` per row/column), `sum`, `count`/`among` (constant
or variable-target condition), `nValues`, `cardinality` (fixed-value/fixed-occurrence form only),
`element`, `ordered`, `lex` (any number of lists, decomposed into consecutive pairwise
`LexConstraint`s beyond two), `cumulative`, `circuit`, `binPacking`, `instantiation` (a
fixed/partial assignment), and single-variable, sum-type, or unweighted maximum-type
`minimize`/`maximize` objectives. Anything else — `regular`/`mdd`, `channel`, `diffn`/`noOverlap`,
other `allDifferent` variants (`Except`, `List`, symbolic), conflict-mode `extension`, a weighted
maximum-type or general expression objective — is not overridden, so it falls through to
`XCallbacks2`'s own default (`unimplementedCase`, a plain `RuntimeException` naming the missing
method), or is explicitly rejected via `UnsupportedXcsp3ConstraintException` when this class
recognizes a construct but can't map a specific *variant* of it (e.g. an
`element`/`cumulative`/`binPacking` condition shaped in a way jcsp's builder has no equivalent
for). Either way, an unsupported instance fails immediately at parse time or first-evaluation
rather than silently returning an under-constrained model. See CLAUDE.md's own XCSP3 section for
the exhaustive, currently-accurate list — this ADR only records the *why* behind the scope
boundary and the decisions that moved it, not a live inventory that would drift out of sync.

`element`'s index and `circuit`'s successor list are 1-indexed in jcsp but not necessarily
0-indexed-or-1-indexed consistently in a given XCSP3 instance (`startIndex` is per-instance);
`Xcsp3CallbackHandler#shiftVariable` builds a small auxiliary variable linked via
`offsetConstraint` to bridge the two conventions, using each variable's real declared bounds
(tracked in `boundsByName`) rather than an arbitrarily wide placeholder domain, since
`IntRangeDomain.of` eagerly materializes its full value set.

## Rejected alternatives

- **Hand-rolled XML + expression parser.** Rejected per Context — reimplementing XCSP3's schema and
  expression grammar is a large, orthogonal undertaking compared to the actual constraint-mapping
  work, and `xcsp3-tools` is the established library real competition solvers (e.g. Choco) already
  build on.
- **A general expression-tree objective** (`buildObjToMinimize(String, XNodeParent<XVarInteger>)`,
  covering an arbitrary `<minimize>` expression rather than a single variable or a sum). Attempted
  first via the same `IntensionExpressionEvaluator` used for `intension`, but
  `BranchAndBoundSolver` calls the objective function on *partial* assignments for pruning, which
  requires the objective to return a sound lower bound on any completion's cost — computing that
  for an arbitrary expression tree needs interval arithmetic over the remaining unassigned
  variables' domains, well beyond this MVP's scope. The single-variable and sum-type-array forms
  both stayed in scope, expressed as genuine `LinearObjective`s (see the next bullet for why that
  matters); the general-expression overloads now throw `UnsupportedXcsp3ConstraintException`
  instead.
- **Negating a `LinearObjective`'s `applyAsDouble` result to express `maximize`.** The first
  attempt at `buildObjToMaximize`'s sum/array form built a positively-signed `LinearObjective` and
  wrapped it in `assignment -> -raw.applyAsDouble(assignment)`. Caught in code review: a lambda is
  never `instanceof LinearObjective`, so `BranchAndBoundSolver.search` falls through to pruning
  directly off `applyAsDouble`'s own "unassigned contributes 0" convention — sound for `minimize`
  under non-negative coefficients/domains, but never sound once negated for `maximize` (the fill
  it would need there is each variable's domain *maximum*, not zero, to stay a valid bound). The
  bug was invisible in tests because they only asserted `solution.isPresent()`, never the actual
  optimum. Fixed by negating the *coefficients* at construction (`buildSumObjective`'s `maximizing`
  flag) instead of the function's result, keeping the objective a genuine `LinearObjective` for
  both directions. That turned out to make the non-negativity restriction unnecessary too, not
  just the negation bug: `LpModelBuilder`'s LP relaxation (which `instanceof LinearObjective`
  unlocks) reads each variable's real domain bounds directly from the `ConstraintSatisfactionProblem`
  rather than going through `applyAsDouble`'s fill convention at all, so it was already sound for
  any coefficient or domain sign — `applyAsDouble`'s "contributes 0" convention only matters for a
  `ToDoubleFunction<Assignment>` that *isn't* recognized as a `LinearObjective`, which no longer
  describes any objective this parser builds. `LinearObjective` itself needed no changes.

- **Rejecting every non-empty `TypeFlag` set on an `extension` constraint.** The first pass at the
  unary (single-variable) form of `extension` rejected any tuple/value list carrying *any*
  `TypeFlag`, on the assumption that a non-default flag always meant something jcsp's plain
  `int[]`/`int[][]` representation couldn't express. Found wrong by running the parser against a
  real instance (`GraphColoring-qwhdec-o5-h10-1` from `xcsp3team/XCSP3-Java-Tools`'s own test
  corpus) using `0..4` range shorthand for a unary support list: `xcsp3-tools` sets
  `TypeFlag.UNCLEAN_TUPLES` for that case, but has *already* expanded the range into a plain,
  fully-materialized array by the time `buildCtrExtension` receives it — the flag is purely
  retrospective metadata about the original XML's notation, not a sign of unfinished work. Fixed to
  reject only `TypeFlag.STARRED_TUPLES`/`SMART_TUPLES` (wildcards / arbitrary expressions, neither
  representable by a plain array), which are the only two flags that genuinely can't be handled.
- **A variable-target `count`/`among` condition shaped like `nValues`'s, not like `sum`'s.** The
  first attempt at `CountVariableConstraint`/`AmongVariableConstraint` (the classes needed once
  `buildCtrCount` had to support a `ConditionVar`, not just `ConditionVal`) copied
  `NValueConstraint`'s always-equality shape: no `Operator` field, with the XCSP3 condition applied
  afterward via a fresh auxiliary variable plus a separate `UnaryComparatorConstraint`/
  `BinaryComparatorConstraint` — mirroring `applyNValuesCondition`'s existing two-step pattern.
  Caught on review: `NValueConstraint` has no `Operator` to generalise *from* (it never had a
  bound-form sibling), whereas `CountConstraint`/`AmongConstraint` already do
  (`count(vars, value) <op> n`), so the direct, consistent generalisation — matching what
  `SumVariableConstraint`/`MaxVariableConstraint` already did for `sum`/`max` — is to keep the
  `Operator` field and swap the fixed bound for a `Variable<Integer>` target. Redone that way:
  `buildCtrCount`'s `ConditionVar` branch collapsed to a single direct call (no auxiliary variable,
  matching `applySumCondition`'s own `ConditionVar` handling), and reification now works on a
  variable-target `count`/`among` for free through `addOrReify` — a strict improvement over `sum`'s
  own variable-target form, which `applySumCondition` explicitly rejects when reified. Deriving the
  corrected classes' bounds-consistency propagation (narrowing the target against the counted
  variables' achievable `[definiteCount, maxCount]` range and vice versa, mirroring
  `MaxVariableConstraint`) surfaced a real soundness bug in `explainInfeasible`: the reason for a
  too-low-`maxCount` infeasibility must cite the *impossible* variables (the ones categorically
  excluded, which is what caps `maxCount`), not the *possible* ones — citing the wrong list would
  have produced a nogood forbidding a target value universally, even in branches where the counted
  variables could take different values. `CountConstraint`/`AmongConstraint`'s own
  `explainInfeasible` already cited the right list; the new classes now match.

## Consequences

- Extending constraint coverage later is additive and mechanical: override one more `XCallbacks2`
  method per new construct, following the existing pattern (`Condition` dispatch on
  `ConditionVal`/`ConditionVar`, shift auxiliary variables via `offsetConstraint` where jcsp's
  indexing convention doesn't match XCSP3's).
- Fixing a genuinely-recognized-but-unmappable constraint variant, or widening a callback from one
  overload to a sibling (unary `extension` alongside the n-ary form, `allDifferentMatrix` alongside
  the plain list, a variable-target `count`/`among` condition alongside the constant one, `lex`
  beyond two lists), stayed scoped to one callback method — and, where the underlying constraint
  class itself needed a variable-target sibling (`count`/`among`, following `sum`/`max`'s existing
  precedent) or a genuinely new capability (`max` against a variable target, needed to express
  `<minimize type="maximum">`), that stayed scoped to one new constraint class plus its `CSP.Builder`
  overload — never a parser rewrite. `Xcsp3ProblemRunner` (single instance) and
  `Xcsp3CompetitionRunner` (a whole batch, one `exec`-ed `Xcsp3ProblemRunner` process per instance
  — `parser.xcsp3` package, test sources; not part of this decision but built to exercise it)
  running against real, unmodified `.xml.lzma` instances bundled from `xcsp3team/XCSP3-Java-Tools`'s
  own test corpus (`src/test/resources/xcsp3/competition/`) was what actually found these gaps, one
  crash at a time — each fix let the *next* instance parse further into its own file before hitting
  the next boundary, rather than surfacing everything a static read of the XCSP3 spec would have.
  Bundling the instances (rather than fetching them ad hoc) is what makes the batch repeatable: the
  same corpus can be re-run after any future parser change to check whether it regresses or moves
  the frontier further.
- `MaxVariableConstraint`/`CountVariableConstraint`/`AmongVariableConstraint` — added specifically
  to unlock XCSP3 coverage (a `<minimize type="maximum">` objective, a variable-target `count`/
  `among` condition) — are genuine, general-purpose additions to jcsp's own constraint library, not
  parser-internal plumbing: each has a public `CSP.Builder` overload usable directly by any caller,
  independent of XCSP3 entirely.
- A real, pre-existing jcsp bug was found and fixed while writing this parser's tests (not part of
  this decision, but load-bearing for its correctness): `TreeSolver` never checked `isConsistent`
  for a tree's root variable when the tree is exactly one node, since `populateAssignment` returns
  immediately via its `isComplete` branch before any filter runs. This was invisible for ordinary
  `UnaryConstraint`s (already pruned out of the domain by `NodeConsistency` before `TreeSolver` ever
  runs) but surfaced immediately for a single-variable `intension` constraint, whose
  `PredicateConstraint` isn't a `UnaryConstraint` and so isn't touched by `NodeConsistency` at all.
