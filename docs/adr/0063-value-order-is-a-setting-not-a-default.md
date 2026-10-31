# 0063. Value order is a setting, not a new default

**Status**: Accepted

## Context

Both chains tried values in `LeastConstrainingValueOrderer` order, fixed in `Solver.Factory`. On
`BinPacking-*` Choco proves the optimum quickly, and one difference is value order: its `ChocoXCSP` default
rotates min / max / random value selectors across restarts, where jcsp always went fewest-conflicts first.

## Measurements

Temporary hooks, removed afterwards. All with a fixed seed, interleaved.

- Reversing the order finds the `BinPacking` optimum 10–30× sooner.
- Alternating the least-constraining order and its reverse per restart (`rr2`), and cycling it with a
  seeded-random order (`rr3`): the refutation took 6.8s and 6.3s against 32.7s unreversed, and the
  optimality proof on `tab` a mean 11.2s against 18.7s over 8 seeds. Re-measured on the factory below with
  plain domain order (no binary constraints, so the same thing on this instance): refuting "at least 6 unused
  bins" took a mean 12.8s in domain order (7 of 8 seeds proved), 3.5s reversed, and 2.9s alternating.
  Reversal alone accounts for the gain there; alternating adds nothing over it.
- The full 60s sweep with `rr2` was **26% slower overall** (747.6s against 591.1s). `PrizeCollecting`
  proved only 3 of 6 seeds (3.4s → 60s), `driverlogw` 3.3s → 9.0s, `Bibd` about 2×. It gained on
  `MagicSquare-9` (16s → 3.5s), `qwh` (9.8s → 4.4s) and `Vrp-A` (1879 → 1470).
  **That regression did not reproduce in isolation.** On the factory's alternating order, over the least
  constraining order, `PrizeCollecting` proved all 6 seeds in about 3.5s against 4.1s, and `Bibd` was faster
  (mean 6.2s against 11.5s over 10 seeds). The sweep's `rr2` hook differed in its details (it keyed off the
  global restart count, which includes the seeder's) and ran under the competition runner's seed schedule;
  which of those, if either, made the difference was not established.
- Choosing the direction online failed. A per-restart bandit and a block bandit, scoring each order by the
  fraction of the space it explored, did not hold up in the sweep. The score does separate the orders in a
  mixed run (mean explored fraction per restart 0.02 for domain order against 0.11 reversed on `BinPacking`),
  so it is not that the signal is lost.
- Keeping each order's dom/wdeg weights and phase memory apart, so one order's learning could not steer the
  other (nogoods stayed shared), did not help: alternating refuted `BinPacking` in a mean 4.9s against 2.9s
  shared, and `Bibd` took 9.6s against 6.2s, because each order adapts on half the evidence. The signal
  separated the orders the same way with and without the split, so shared state is not what confounded it.
  Split state with the order left alone was fast on `Bibd` (mean 4.1s against 11.5s, 7 of 10 seeds clearly
  better, none clearly worse), which looks like diversification from restarting the weights, not value
  order; ten seeds of one instance is a lead and was not pursued.

## Decision

`SolverConfig.valueOrder` is a `DomainValuesOrderer.Factory` and defaults to `LEAST_CONSTRAINING`, so
nothing changes for anyone who does not set it.

The factory shape, rather than a bare orderer, follows `AdaptiveVariableSelector.Factory` (ADR-0038): an
order may keep per-search state, so `create()` is called for each search. `DomainValuesOrderer` gains
`default void onRestart(int)`, called by `DomWdegLubySearch` and `BranchAndBoundSolver` beside the
selector's, so an order can vary by restart without owning the restart machinery.

Provided: `LEAST_CONSTRAINING`, `DOMAIN_ORDER`, `REVERSED_DOMAIN_ORDER`, `ALTERNATING` (domain order and its
reverse), plus the combinators `reversed(factory)` and `cycle(factory, ...)`, which take any factory and so
extend to orders added later (a random order, a solution-guided one) without new plumbing, and let a caller
alternate the *default* order with `cycle(LEAST_CONSTRAINING, reversed(LEAST_CONSTRAINING))`. They are named
for *domain order*, not ascending, because a domain iterates in the order it was given.

## Why not a new default

The `BinPacking` gain is real and the sweep regression is unexplained, which is a worse position than
either. A default that is 26% slower on the corpus in one measurement and faster in the isolated ones is not
one to change without understanding the difference, and the offline evidence for choosing a direction
online is negative.

## Consequences

- A user with a capacity-packing model can select `REVERSED_DOMAIN_ORDER` or `ALTERNATING`; nobody is pushed
  to it.
- The remaining gap to Choco on `BinPacking` is not attributable to one thing: the value-order portfolio,
  phase saving, restart nogoods, lex, and the two cardinality propagators all contribute.
- Reproducing the sweep regression under the runner, to learn why `rr2` broke `PrizeCollecting` there, is the
  open question that would let the default be revisited.
