# ADR-0027: XCSP3's `circuit` is a sub-circuit, not a Hamiltonian circuit

**Status:** Accepted (2026-09-20)

## Context

`Xcsp3CallbackHandler#buildCtrCircuit` mapped XCSP3's `circuit` onto `CircuitConstraint`, whose
contract is *"a single **Hamiltonian** circuit through all n nodes ... equivalent to MiniZinc's
`circuit`"*.

The two constructs are not the same. XCSP3's `circuit` lets a node sit out by pointing at itself,
and requires only that the remaining nodes form one circuit. Mapping it to the Hamiltonian reading
makes the model strictly stronger than the instance, so valid solutions are rejected and the solver
reports a false `UNSATISFIABLE`.

Both corpus instances that use `circuit` were wrong:

| Instance | Real solution | jcsp said |
|---|---|---|
| `Mario-easy-4` | circuit over 14 of 15 nodes, node 12 self-loops | UNSATISFIABLE |
| `Tpp-3-3-20-1` | circuit over 8 of 9 nodes, node 7 self-loops | UNSATISFIABLE |

A 100% failure rate on the construct, undetected because nothing in the project could see it. A
false UNSAT has no solution for `SolutionChecker` to reject, and jcsp's parser and its validation
both go through `xcsp3-tools`, so a construct read wrongly by the parser is not caught by the
checker either. It surfaced only when a second, independently implemented solver was run over the
same corpus and its answers cross-checked — Choco solved both, and the checker accepted its
solutions.

Two independent sources fix the intended semantics. Choco's own XCSP3 parser maps `buildCtrCircuit`
to `model.subCircuit(...)`, never `circuit`, documenting *"`vars[i] = offset+i` means that i is not
part of the circuit"*. And `SolutionChecker` itself enforces exactly three conditions: the
successors are distinct, not every node is a self-loop, and a walk from a non-loop node visits
exactly the non-loop nodes.

## Decision

Add `SubCircuitConstraint` and map XCSP3's `circuit` to it. `CircuitConstraint` is unchanged and
keeps the Hamiltonian/MiniZinc reading for direct API users, where it is the constraint most callers
actually want.

`SubCircuitConstraint` enforces the checker's three conditions directly. Propagation is:

- the duplicate-successor pass, shared with `CircuitConstraint` via the new package-private
  `CircuitPropagation` — both constraints require a permutation, a sub-circuit plus self-loops on
  the excluded nodes being a bijection just as a Hamiltonian circuit is;
- a closure pass: once the fixed travelling (non-self-loop) nodes are closed under successor, no
  further node can join them, so they are the whole circuit and every node outside is forced to
  self-loop. A node outside that cannot self-loop makes the closure infeasible, and two or more
  disjoint circuits are infeasible outright;
- an emptiness pass: once every node is fixed to its own self-loop, the circuit is empty.

Conflict explanations cite the node their conclusion rests on, not only the circuit — that an
outside node cannot self-loop is a fact about *that node's* domain, and citing the circuit alone
would assert it is contradictory on its own when it is not. This is the same discipline ADR-0002's
explanation mechanism requires everywhere, and the same defect fixed in `InverseConstraint` in the
commit preceding this one.

The size-constrained forms, `circuit(list, size)` with a constant or variable size, now throw
`UnsupportedXcsp3ConstraintException` with a message naming the construct, rather than falling
through to the library's own "not overridden" failure. Supporting them needs a circuit-length term
`SubCircuitConstraint` does not carry.

## Consequences

`Mario-easy-4` and `Tpp-3-3-20-1` both now report `OPTIMUM FOUND`, at 545 and 126 — matching Choco's
optima exactly, both validated by `SolutionChecker`. Full suite green at 3341 tests with the
coverage gate satisfied.

The broader consequence is about measurement, not this constraint. `Xcsp3CompetitionRunner`'s
`category()` counts `s UNSATISFIABLE` as SOLVED, so both of these instances were being counted as
successes while being wrong, and so was `Blackhole-04-3-00` before the `InverseConstraint` fix. Any
"N of 85 solved" figure quoted before this ADR includes at least three wrong answers. A solved-count
that cannot distinguish a proof from a mistake is not a correctness measure, and nothing inside this
project can supply the missing half — only a second solver can.

## Rejected alternatives

- **Widening `CircuitConstraint` to permit self-loops.** It is a public API constraint documented as
  MiniZinc's `circuit`, and `Prob075ProductMatrixTspTest` depends on the Hamiltonian reading — a TSP
  tour that may skip cities is not a TSP tour. Weakening it would silently break every existing
  caller to fix one parser mapping.
- **A mode flag on `CircuitConstraint`.** Rejected for the reason ADR-0006 keeps constraint
  compatibility keyed on concrete classes: a flag makes the whitelist, the propagator registry and
  `isSatisfiedBy` all branch on a field, where two classes make each variant's semantics checkable
  in isolation. It would also make `getRelation()` ambiguous in logs.
- **Implementing the size-constrained forms now.** No corpus instance uses them, and guessing at a
  circuit-length term with nothing to validate it against is how this bug was introduced in the
  first place.
