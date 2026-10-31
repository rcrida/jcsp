# 0062. Lex chains are an opt-in constraint; the parser keeps emitting pairs

**Status**: Accepted

## Context

An XCSP3 `<lex>` over a matrix, or a `lexMatrix`, orders rows `r1 ≤ r2 ≤ … ≤ rm`. The parser posted that as
`m-1` pairwise `LexConstraint`s. Choco posts one `PropLexChain`, which sees all the rows at once.

Pairs are weaker: each pair prunes with only its two rows' bounds, so a value that no chain of rows can
support survives. A chain propagator computes, for each row, the least vector it can take given the rows
before it and the greatest given the rows after it, and clips the row between them.

## Decision

`LexChainConstraint` (`constraints.nary`, builder method `lexChainConstraint(rows, operator)`) is a
`Propagatable` over the whole list of rows, registered in `FixpointPropagation`'s catalogue. It is the same
algorithm as Choco's `PropLexChain`: lower vectors computed down the chain and upper vectors back up, each
value-aware (a vector is the nearest one the domains allow), then every row clipped to its interval. One
pass suffices unless a variable repeats, where it loops. It is within about 1% of hyperarc consistency
(an oracle over random chains found an unsupported value left in 0–0.8% of cases), and it does not do
Choco's extra tail-filtering step.

**The parser is not changed.** `buildCtrLex` and `buildCtrLexMatrix` still emit pairs. A caller who wants the
chain builds it.

## Why opt-in

Same seed, interleaved, on the bundled corpus:

| Instance | Effect of the chain |
|---|---|
| `Opd` | 82% fewer nodes, on 8 of 8 seeds |
| `Bibd` | worse, mean nodes up about 170% |
| `BinPacking-*` | mixed to worse |
| `SocialGolfers` | slightly worse |
| `Cutstock` | unchanged |

The chain costs about 4.4% of samples in a real search against about 1.5% for the pairs, and the stronger
pruning does not repay it except where symmetry is the whole difficulty. Static symmetry breaking also
interacts with a dynamic variable order, and the literature has no rule for choosing between a chain and
pairs (Carlsson & Beldiceanu 2002 gives the algorithm, not the choice). No cheap property of an instance
that separated the wins from the losses was found, so the default stays with what is cheaper.

Tried and dropped on the way: a stronger *pairwise* lex (tie implies strict) was sound and cheap but gave no
corpus gain; a static "lex-friendly" variable order was worse; excluding the chain from dom/wdeg's weights
did not change the result, which rules out weight dilution as the cause.

## Consequences

- A model with a symmetric matrix can opt in and gets the stronger propagation.
- Nothing changes for existing users or for the corpus.
- If a rule ever separates the winning instances, the parser can pick per instance without an API change.
