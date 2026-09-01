package io.github.rcrida.jcsp.constraints.nary;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Value-level generalized-arc-consistency narrowing for {@code Σ coeffs[i]*vars[i] == bound} over
 * discrete domains — strictly stronger than {@link LinearBoundPropagation}'s own bounds-only
 * {@code EQ} pass, which only reasons about {@code totalMin}/{@code totalMax} and can leave a
 * value in a variable's domain that is numerically within range but not actually reachable by any
 * combination of the other variables' current values (e.g. {@code x1 ∈ {0,3}, x2 ∈ {0,5},
 * x1+x2==4}: bounds consistency sees the achievable range {@code [0,8]}, which contains {@code 4},
 * but no real combination of live values sums to it). {@link LinearBoundPropagation}'s {@code
 * LEQ}/{@code GEQ} passes need no such treatment for the same reason {@link
 * ExtremumPropagation#narrowMaxEqCoverage} documents for {@code max}/{@code min}: an inequality
 * only needs <em>some</em> achievable value to exist, and each variable's own true extreme is
 * always a real, currently-present value — no coverage gap is possible there. Only {@code EQ}
 * needs this.
 * <p>
 * {@link #computeSubsetSumCoverage} is a forward-backward DP over reachable partial sums, the same
 * shape as {@link RegularConstraint}'s forward-backward DP over reachable/productive automaton
 * states, just over achievable sums instead of DFA states: {@code forward[i]} is the set of sums
 * achievable using terms {@code 0..i-1}, {@code backward[i]} the mirror image using terms {@code
 * i..n-1}. A candidate value {@code v} for term {@code i} is GAC-supported iff {@code bound -
 * coeffs[i]*v} is expressible as {@code s1 + s2} for some {@code s1} reachable in {@code
 * forward[i]} and {@code s2} reachable in {@code backward[i+1]}.
 * <p>
 * Deliberately a pure array-level module (no {@link io.github.rcrida.jcsp.domains.Domain}/{@link
 * io.github.rcrida.jcsp.variables.Variable} dependency) — {@link LinearBoundPropagation} owns
 * extracting live values from real domains and narrowing them back, the same split {@link
 * ExtremumPropagation}'s own {@code narrowMaxEqCoverage}/{@code propagateEqCoverage} pair uses,
 * just carried one step further here since this class needs no {@code Domain}-level counterpart at
 * all.
 */
final class SubsetSumCoveragePropagation {
    private SubsetSumCoveragePropagation() {}

    /**
     * Cap on the achievable-sum range ({@code totalMax - totalMin + 1}) {@link
     * #computeSubsetSumCoverage} is willing to build reachability bitsets over. A nonzero integer
     * coefficient maps each of a variable's own distinct live values to a distinct point within
     * this range, so this single cap also bounds live-value enumeration cost per variable —
     * no separate per-domain cardinality guard is needed. Starting value chosen to comfortably
     * cover the XCSP3 competition corpus instances this pass was added for (targets in the
     * hundreds); revisit after benchmarking a wider corpus.
     */
    static final int MAX_REACHABLE_RANGE = 200_000;

    /**
     * Whether {@link #computeSubsetSumCoverage} is worth attempting: the achievable-sum range is
     * small enough for its reachability bitsets to stay bounded. Does <em>not</em> check the
     * operator itself — {@link LinearBoundPropagation} only calls this once it has already
     * restricted to the {@code EQ} case, the only one with a coverage gap to close.
     *
     * @param totalMin the constraint's own achievable minimum sum (as {@link
     *                  LinearBoundPropagation} already computes for its bounds pass)
     * @param totalMax the constraint's own achievable maximum sum
     */
    static boolean eligible(int totalMin, int totalMax) {
        long range = (long) totalMax - totalMin + 1;
        return range <= MAX_REACHABLE_RANGE;
    }

    /**
     * Forward-backward subset-sum DP. {@code values.get(i)} is term {@code i}'s own currently-live
     * values (parallel to {@code coeffs}); {@code minContribs[i]}/{@code maxContribs[i]} are each
     * term's own achievable contribution range ({@code coeffs[i]*v} minimized/maximized over its
     * <em>declared</em> domain — safe to pass a wider range than {@code values.get(i)}'s actual
     * live set, e.g. from before a sibling bounds-consistency pass narrowed it, since every real
     * {@code v} in {@code values.get(i)} was already in that wider domain and so still satisfies
     * {@code coeffs[i]*v >= minContribs[i]}, the invariant the DP's own offset arithmetic relies
     * on to keep every bitset index non-negative).
     *
     * @return {@link Optional#empty()} if no combination reaches {@code bound} at all (a case
     *         bounds consistency alone can miss for gapped domains); otherwise the exact retained
     *         value set for every term, parallel to {@code values} — a term whose retained set
     *         comes back empty also signals infeasibility, since that term's own values were
     *         tested against a bound-consistent, in-range {@code bound}, so an empty result there
     *         is real, not an artifact of the initial range check
     */
    static Optional<List<Set<Integer>>> computeSubsetSumCoverage(
            List<int[]> values, int[] coeffs, int[] minContribs, int[] maxContribs, int bound) {
        int n = coeffs.length;

        // cumMin[i]/cumMax[i]: achievable range using terms 0..i-1 (forward[i]'s own offset/width).
        int[] cumMin = new int[n + 1];
        int[] cumMax = new int[n + 1];
        for (int i = 0; i < n; i++) {
            cumMin[i + 1] = cumMin[i] + minContribs[i];
            cumMax[i + 1] = cumMax[i] + maxContribs[i];
        }
        // sufMin[i]/sufMax[i]: achievable range using terms i..n-1 (backward[i]'s own offset/width).
        int[] sufMin = new int[n + 1];
        int[] sufMax = new int[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            sufMin[i] = sufMin[i + 1] + minContribs[i];
            sufMax[i] = sufMax[i + 1] + maxContribs[i];
        }

        BitSet[] forward = new BitSet[n + 1];
        forward[0] = new BitSet(1);
        forward[0].set(0);
        for (int i = 0; i < n; i++) {
            int width = cumMax[i + 1] - cumMin[i + 1] + 1;
            BitSet next = new BitSet(width);
            for (int v : values.get(i)) {
                int delta = coeffs[i] * v - minContribs[i];
                for (int idx = forward[i].nextSetBit(0); idx >= 0; idx = forward[i].nextSetBit(idx + 1)) {
                    next.set(idx + delta);
                }
            }
            forward[i + 1] = next;
        }

        BitSet[] backward = new BitSet[n + 1];
        backward[n] = new BitSet(1);
        backward[n].set(0);
        for (int i = n - 1; i >= 0; i--) {
            int width = sufMax[i] - sufMin[i] + 1;
            BitSet next = new BitSet(width);
            for (int v : values.get(i)) {
                int delta = coeffs[i] * v - minContribs[i];
                for (int idx = backward[i + 1].nextSetBit(0); idx >= 0; idx = backward[i + 1].nextSetBit(idx + 1)) {
                    next.set(idx + delta);
                }
            }
            backward[i] = next;
        }

        List<Set<Integer>> kept = new ArrayList<>(n);
        boolean anyEmpty = false;
        for (int i = 0; i < n; i++) {
            BitSet fSet = forward[i];
            BitSet bSet = backward[i + 1];
            int fOffset = cumMin[i];
            int bOffset = sufMin[i + 1];
            boolean iterateForward = fSet.cardinality() <= bSet.cardinality();

            Set<Integer> keptForTerm = new HashSet<>();
            for (int v : values.get(i)) {
                int dLocal = bound - coeffs[i] * v - fOffset - bOffset; // need idx1 + idx2 == dLocal
                boolean supported = false;
                if (iterateForward) {
                    for (int idx1 = fSet.nextSetBit(0); idx1 >= 0; idx1 = fSet.nextSetBit(idx1 + 1)) {
                        int idx2 = dLocal - idx1;
                        if (idx2 >= 0 && bSet.get(idx2)) { supported = true; break; }
                    }
                } else {
                    for (int idx2 = bSet.nextSetBit(0); idx2 >= 0; idx2 = bSet.nextSetBit(idx2 + 1)) {
                        int idx1 = dLocal - idx2;
                        if (idx1 >= 0 && fSet.get(idx1)) { supported = true; break; }
                    }
                }
                if (supported) keptForTerm.add(v);
            }
            if (keptForTerm.isEmpty()) anyEmpty = true;
            kept.add(keptForTerm);
        }

        return anyEmpty ? Optional.empty() : Optional.of(kept);
    }
}
