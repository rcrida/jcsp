# 0072. One unheld value per node when values are interchangeable

**Status**: Accepted

## Context

In a graph colouring, or a Schur-number partition, the values are names: renaming them maps every
solution to a solution and every dead end to a dead end. A search that refutes a partial colouring
must then refute each of its renamings too. `color_X2`, an uncolourable graph, took 6.6s to 30.1s and
30,757 to 139,692 nodes over three seeds; Choco refutes it in 1.9s.

## Decision

`DomWdegLubySearch.getSolution` asks `InterchangeableValues.hold` once per call whether the problem's
values are interchangeable: every variable has the same discrete domain of at least two values, and
every constraint other than a learned nogood is a variable-to-variable equality or inequality, an
all-different, a `RelationLogicConstraint` whose two literals compare variables by `=` or `≠`, or a
table or conflict table closed under renaming. A table is closed when, for every pattern of equal
positions it holds, it holds all of that pattern's renamings. When they are interchangeable, each
node's candidates keep the values some variable already holds and only the first unheld one.

This stays complete. Unheld values are interchangeable given the assignment, and every pruning is
entailed by the problem. So if one unheld value has been pruned, every other unheld value fails too,
and trying one of them is enough. That holds under dynamic variable ordering, restarts, phase memory
and restart nogoods.

It is not applied in `getSolutions`, which must return every solution, renamings included.

## Rejected alternatives

- **Expecting it to cut a satisfiable search.** This was built for `schur_X2`, whose seed-to-seed
  spread (0.33s to 21.7s, against Choco's steady 0.30s) looked like the same dead ends being explored
  under different colour names. It changed nothing there: identical node counts on five seeds. All
  five colours are in use within a run's first few decisions, after which no value is unheld, and a
  run reaches a restart before backtracking to those first levels. The rule pays off in a refutation,
  which has to return to the top of the tree; `schur_X2`'s variance lies elsewhere.
- **Symmetry-breaking constraints**, such as value precedence. They would apply in `getSolutions`
  too, where they are wrong, and they fix one canonical order that dom/wdeg's dynamic order must then
  fight; the branching rule follows whatever order the search chooses.
- **Detecting partial interchangeability**: values interchangeable among a subset of variables, or
  after preprocessing narrows some domains unevenly. Sound versions exist but need the symmetry
  tracked per value set; no bundled instance needs it yet.

## Consequences

Three bundled problems are detected as interchangeable when parsed: `ColouredQueens-07`,
`color_X2` and `schur_X2`. Over three seeds `color_X2` takes 0.89–1.15s and 651–2,268 nodes, faster
than Choco; `ColouredQueens-07` and `schur_X2` are unchanged.

Four restart tests built their long refutations from colourings of complete graphs, which the rule
now refutes quickly. Each fixture gains one variable fixed to a value of its own: it is assigned at
the root without a node, and its different domain turns the rule off, leaving the search those tests
describe.
