# 0060. Variables are issued by their problem and carry a dense index

**Status**: Proposed

## Context

A `Variable` is a name-only value (`Variable.Impl`, compared by `String.equals`), so the solver
keys 139 main-source classes' worth of `Map<Variable<?>, …>` and `Set<Variable<?>>` by hashing a
string. Several places have already worked around that by hand: `DomainMap` keeps an array of
domains plus a `Positions` hash map from variable to slot, and `DomWdegVariableSelector` keeps its
own `Variable<?>[]` and index map. Each of these re-derives an index the problem could have given
out once.

`Variable` is also an open interface, and tests and examples implement it with their own records
(`GroupSubject`, a collision fixture in `OverlayAssignmentTest`). Identity-comparable, indexed
variables cannot be open to implementation.

## Decision

Proposed; not yet implemented beyond the scaffolding noted under Consequences.

- A problem's variables are issued by the problem's own builder: `csp.variable(name, domain)`
  replaces `createVariable` and `variableDomain`, and the 1-d and 2-d array helpers move with it.
  A variable therefore never exists without a domain, and a duplicate name in one builder is
  rejected.
- A variable carries `getIndex()`, its position in the order the builder issued it, dense from 0.
  Per-variable state in the solver becomes an array over that index rather than a hash table.
- Comparison is by identity within the problem that issued it. A variable handed to a builder that
  did not issue it is rejected at the builder boundary with an `assert`.
- A derived problem (`withVariableSubset`, `decomposeSubproblems`, tree-decomposition cliques)
  keeps its parent's index space, so its arrays are sized to the parent and are sparse. Reindexing
  per subproblem would need a remapping at every boundary and would cancel the saving.
- Variables created mid-solve (tree-decomposition separators, XCSP3 auxiliaries, reification
  indicators) are issued by a child factory that continues the parent's counter.
- `Variable` stops being an interface for callers to implement. Domain-keyed variables such as
  `GroupSubject` become a `Map<Key, Variable<T>>` held by the caller.

## Rejected alternatives

- **A global counter on `Variable.Factory.INSTANCE`.** Indices stay unique but are not dense per
  problem, so an array sized by index grows with every variable the JVM has ever made. Fine as a
  transitional state while nothing reads the index; useless as the end state.
- **Reindexing each subproblem densely.** Smaller arrays, but every boundary between a problem and
  its subproblems then translates variables, and `Assignment` crosses those boundaries constantly.
- **Keeping `Variable` open and adding `index()` for implementors to supply.** Pushes the
  uniqueness and density invariants onto every caller, which is the problem being removed.

## Consequences

To be measured before the migration is committed to beyond `Assignment` and `DomainMap`: the
claim is that array lookup beats hashing a name, not that any particular instance gets faster
(see the corpus-sweep protocol used by ADR-0024 and ADR-0058).

Scaffolding landed first: `Variable.getIndex()` exists, and `Variable.Factory` is stateful: a
`new Factory()` numbers its variables from 0 and rejects a name it has already issued. Each builder
owns a factory, a built problem keeps it, and `toBuilder()` hands the new builder `fork()` of it: a
factory that numbers on from the parent's next index and treats the parent's names as issued by
following a parent link, so neither is copied. Forks of one parent are independent of each other.
Nothing reads the index yet. `Variable.Factory.INSTANCE` remains the shared factory until the builder migration
retires it; it numbers uniquely but not densely and accepts repeated names, because the existing
tests reuse names across problems.

`DomainMap` was converted first, to two arrays indexed by `getIndex()` with no hash map: the
variables, so a lookup can check it was handed exactly one of the map's own, and the domains. It
iterates in index order, which is declaration order for builder-issued variables, and builds with
an `IllegalArgumentException` for a variable with no index or two sharing one. Interleaved A/B
against the commit before it, seed 12345, 5 pairs each:

| Instance | Result |
|---|---|
| `driverlogw-09`, satisfaction, 650 variables | 2.49s → 2.44s, identical 18,634 nodes |
| `Taillard-os-04-04-0`, optimality proof | 3.42s → 3.33s, identical 41,158 nodes |
| `Vrp-A-n32-k5`, optimization, 33 components | 66.2k → 73.0k nodes in 10s, up in 5 of 5 pairs |
| `Vrp-P-n16-k8`, optimization, 17 components | 241k → 265k nodes in 10s, up in 5 of 5 pairs |
| 400 independent 10-queens, indices interleaved | 1061ms → 1075ms, within noise |

The sparse-subproblem worry is only partly tested. The Vrp instances decompose but run on the
optimization chain, whose node loop works on the whole problem, and the synthetic case does little
search per component, so a component needing heavy search is the case still unmeasured. The
alternative if it regresses is to reindex a subproblem densely at the decomposition boundary.

`Variable.Impl` then became identity-equal. Its hash code is the index, not the identity hash, so a
hash set or map of variables iterates in the same order on every run; the identity hash would have
made that order, and with it the decomposer's tree-variable names, differ per JVM. The index has to
be spread across the 32-bit range first (multiplied by an odd constant): `Valuations#hash` sums a
variable's hash with its value's, and with bare indices `Steiner3-08`'s 80,640 six-tuples collapsed
into a few hash codes again, so parsing took 28 seconds to honour a 5 second limit instead of 7.

Obligations: every test that builds variables through `Variable.Factory` migrates to the builder;
`equals`/`hashCode` of `Assignment`, constraints and `ConstraintSatisfactionProblem` are revisited
once identity replaces name equality.
