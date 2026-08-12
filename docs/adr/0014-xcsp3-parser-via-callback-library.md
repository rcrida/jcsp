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
a numeric evaluator for objectives (removed — see Consequences). It covers only the
arithmetic/relational/boolean operators exercised by this project's XCSP3 test fixtures, not the
full expression language (no `min`/`max`/`dist`/`xor`/`iff`/set operators) — an unrecognized
operator throws `UnsupportedXcsp3ConstraintException` rather than mis-evaluating silently.

Scope is a narrow MVP: variable domains, `intension`, `extension` (support/positive tables only),
`allDifferent`, `sum`, `count`/`among`, `element`, `ordered`, `lex`, `cumulative`, `circuit`,
`binPacking`, and single-variable or sum-type `minimize`/`maximize` objectives. Anything else —
`regular`/`mdd`, `nValues`, `cardinality`, `channel`, `diffn`/`noOverlap`, conflict-mode
`extension`, `group`/`slide`, general expression objectives, `instantiation` — is not overridden,
so it falls through to `XCallbacks2`'s own default (`unimplementedCase`, a plain `RuntimeException`
naming the missing method), or is explicitly rejected via `UnsupportedXcsp3ConstraintException`
when this class recognizes a construct but can't map a specific *variant* of it (e.g. an
`element`/`count`/`cumulative`/`binPacking` condition shaped in a way jcsp's builder has no
equivalent for). Either way, an unsupported instance fails immediately at parse time rather than
silently returning an under-constrained model.

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

## Consequences

- Extending constraint coverage later is additive and mechanical: override one more `XCallbacks2`
  method per new construct, following the existing pattern (`Condition` dispatch on
  `ConditionVal`/`ConditionVar`, shift auxiliary variables via `offsetConstraint` where jcsp's
  indexing convention doesn't match XCSP3's).
- Fixing a genuinely-recognized-but-unmappable constraint variant (e.g. supporting negative/conflict
  extension tables, or a variable-target `count`/`element` condition) is scoped to one method each,
  not a parser rewrite.
- A real, pre-existing jcsp bug was found and fixed while writing this parser's tests (not part of
  this decision, but load-bearing for its correctness): `TreeSolver` never checked `isConsistent`
  for a tree's root variable when the tree is exactly one node, since `populateAssignment` returns
  immediately via its `isComplete` branch before any filter runs. This was invisible for ordinary
  `UnaryConstraint`s (already pruned out of the domain by `NodeConsistency` before `TreeSolver` ever
  runs) but surfaced immediately for a single-variable `intension` constraint, whose
  `PredicateConstraint` isn't a `UnaryConstraint` and so isn't touched by `NodeConsistency` at all.
