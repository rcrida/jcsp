# ADR-0026: Solution-guided phase saving, recorded from the deepest path only

**Status:** Accepted (2026-09-19)

## Context

`DomWdegLubySearch#getSolution` restarts on a Luby schedule. Two things already survive a restart --
the dom/wdeg constraint weights and the shared `NogoodStore` -- but the *value* ordering does not:
each attempt re-derives which value to try for each variable from scratch, via `DomainValuesOrderer`.
So a restart discards everything the previous attempt learned about which values were workable and
pays nodes rediscovering the same partial assignment.

## Decision

Add `PhaseMemory`, carried across restarts alongside the nogood store and the selector's weights, and
consulted by `searchOne` to move a variable's remembered value to the front of its candidate list.

Crucially, the memory is written **only from the deepest assignment reached so far** -- solution-guided
search (Demirović et al.) -- not from every branch that survived propagation, which is the classic
every-descent form of phase saving (Pipatsrisawat & Darwiche).

Only `getSolution` consults it. `getSolutions` returns a complete stream, never restarts, and so has
nothing to recover; its value ordering is deliberately left exactly as it was.

Reordering candidates cannot affect soundness or completeness -- every value is still tried. It does
change which solution a satisfiable problem returns first, which the API does not promise.

## Consequences

Whole-corpus sweep (85 instances, 20s, the runner's fixed seed): **68 solved, up from 65**.
`LangfordBin-08`, `MagicSquare-9-f10-01` and `qcp-15-120-00_X2` newly solved; zero solved instances
lost, zero changed answers, zero `SolutionChecker` mismatches.

Interleaved A/B, fixed seed, on restart-heavy instances:

| Instance | Before | After | |
|---|---|---|---|
| MagicSquare-6-sum | 2,413 nodes, 1,838 ms | 328 nodes, 497 ms | 3.7x faster |
| driverlogw-09 | 12,279 nodes, 22.0 s | 6,957 nodes, 12.1 s | -45% |
| Bibd-sc-06-050-25-03-10 | 2,322 nodes, 799 ms | 1,663 nodes, 618 ms | -23% |
| Bibd-sum-06-050-25-03-10 | 2,322 nodes, 725 ms | 1,663 nodes, 583 ms | -20% |

## Rejected alternatives

- **Recording on every descent that survived propagation**, the classic phase-saving form. Built
  first, and it is *faster where it works*: `MagicSquare-6-sum` 5x (against 3.7x for the variant
  shipped) and `driverlogw-09` 7x (against 45%). It was rejected because it **stopped two instances
  solving at all**: `Bibd-sum-06-050-25-03-10` and `Bibd-sc-06-050-25-03-10` each went from
  SATISFIABLE in under a second to UNKNOWN after 40 seconds, reproducibly, on both repetitions.
  Every-descent recording keeps aiming the next restart back down branches that were locally
  consistent but later refuted; on a highly symmetric design problem that is precisely the trap the
  restart exists to escape. Depth is a monotone proxy for progress, so requiring a strictly deeper
  path before overwriting the memory keeps most of the upside and removes the failure mode.
- **Consulting the memory from `getSolutions` too.** No restarts there to recover from, so it would
  only perturb the order a complete enumeration emits solutions in, for nothing.
- **Clearing the memory on backtrack.** That is the opposite of the mechanism: the value was
  consistent as far as propagation could tell, and the point is to preserve that across the restart.
