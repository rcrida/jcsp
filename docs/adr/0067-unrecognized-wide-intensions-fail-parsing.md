# 0067. An unrecognized intension over three or more variables fails parsing

**Status**: Accepted

## Context

An XCSP3 `<intension>` that no `ConstraintRecognizer` matched and tabulation declined became an
n-ary `PredicateConstraint`. That constraint is checked only once every variable in it is
assigned, so it prunes nothing. A missing recognizer therefore showed up as a slow solve, often
across a whole series, rather than as an error. Every such gap found so far had to be chased
down by hand from a performance symptom.

The fallback fired on every arity. A one-variable intension became a `UnaryPredicateConstraint`,
which node consistency filters completely before search, so it costs nothing. A two-variable one
became the n-ary form too, even though `BinaryPredicateConstraint` would have taken part in
arc consistency.

A probe of every bundled instance (138) and the local series pool (1,837) counted what the
fallback built:
- n-ary `PredicateConstraint`: 38 instances. 37 had only two-variable cases: 36 Haystacks (an
  a≠b written as `gt(0,mul(sub(a,b),sub(b,a)))`) and pack_X2. One instance, radi_X2, had wider
  ones.
- `UnaryPredicateConstraint`: 15 instances.
- `BinaryPredicateConstraint`: none.

## Decision

The fallback is chosen by the number of distinct variables in the expression:
- one becomes a `UnaryPredicateConstraint`;
- two become a `BinaryPredicateConstraint`, so arc consistency revises them;
- three or more throw `UnsupportedXcsp3ConstraintException`, naming the constraint and the
  expression, so the gap surfaces as a parse failure and points at the recognizer to write.

## Rejected alternatives

- **Keep the silent n-ary fallback.** It keeps every instance parsing, but a gap stays invisible
  until someone profiles a slow series.
- **Throw for two variables as well.** Two-variable shapes can be propagated generically by arc
  consistency, so refusing them would turn Haystacks and pack_X2 into parse failures for no
  propagation gain.

## Consequences

- radi_X2 now fails parsing; it was UNKNOWN before.
- Haystacks gets arc consistency on its disguised a≠b, but this was measured to change nothing:
  Haystacks-19 to 22 stay UNKNOWN at 60s. Haystacks-07 to 18, whose a≠b was already tabulated
  with full GAC, also time out, so that family is hard for other reasons. The change is for
  failing fast, not for speed.
- A new recognizer or tabulation still takes precedence over every fallback.
- The fallback is wired by arity, not by shape, so a two-variable expression is propagated
  correctly but more slowly than a dedicated constraint would propagate it.
