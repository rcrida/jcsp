# 0073. Break the first run's ties by degree

**Status**: Accepted

## Context

At the root of a search every constraint weight is 1, so dom/wdeg ranks variables by domain size
over degree, and in a problem whose variables share one domain and similar degrees most of them
tie. Each restart broke those ties at random from its seeded source (ADR-0015). That made the first
run, and the weights and phase memory it hands to the next, depend on the seed.

On `schur_X2` that was the whole difference between a fast and a slow solve: 0.35s, 20.9s and
15.4s on seeds 1 to 3, against Choco's steady 0.30s. ADR-0072 had ruled out value symmetry as the
cause.

Tie-breaking was also a private detail of `DomWdegVariableSelector`, so trying another rule meant
editing the selector.

## Decision

Tie-breaking is its own strategy, `TieBreak`, built per solve by a `TieBreak.Factory` and passed to
`DomWdegVariableSelector` and `CutsetFirstSelector`. The selector ranks and the tie-break chooses
among equals, so either can be swapped without touching the other. `AdaptiveVariableSelector.Factory`
gains `cutsetFirst(tieBreaks)` and `domWdeg(tieBreaks)`.

Three tie-breaks compose:
- `RANDOM`, the previous behaviour.
- `maxDegree(then)`, which keeps the tied variables in the most structural constraints and hands the
  rest to `then`.
- `firstRun(first, later)`, which uses `first` until the second restart begins.

The default, `TieBreak.Factory.DEFAULT`, is `firstRun(maxDegree(RANDOM), RANDOM)`. The selectors'
one-argument constructors keep `RANDOM`.

## Rejected alternatives

- **Degree in every run.** `schur_X2` became seed-independent, but over the competition, series and
  tree corpora on three seeds the pooled time ratios were 1.017, 0.957 and 0.967. `Primes-20-40-2-7`
  ran 3.9× slower and `BinPacking-mdd-n1c1w4a` 4.3× slower on one seed, and lost its optimum on
  another. Fixing the order in every run removes the variety restarts exist to provide.
- **Changing dom/wdeg's ranking itself**, for example a weighted-degree term. The live weighted
  degree is already in the ratio; it is only at equal ratios that the seed decides.

## Consequences

On the same three corpora and seeds no instance changed status. The pooled time ratios against
random tie-breaking were 0.914 (competition), 0.904 (series) and 1.189 (tree). The tree figure rests
on three instances and is driven by `Rlfap-graph-13-w1`, which swings between two speeds.

`schur_X2` takes 0.66s to 1.27s on ten seeds, after five to seven restarts. The ordered first run
does not solve it; it leaves weights and a phase memory the random runs then finish from. Both
`Bibd` instances on seed 2 went from 42s to 3.4s.

The cost is lost luck on some heavy-tailed instances. On fifteen seeds `geometric-50-20-d4-75-55`
has about the same median (67k nodes against 74k) and a lower worst case (148k against 204k). But it
never repeats random tie-breaking's three runs under 7k nodes, so its geometric mean is 1.68× worse.
`Fischer-04-06-fair` looked 1.2–2.1× slower on three seeds but is within its spread over five.
