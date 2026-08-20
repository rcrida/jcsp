package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A unary-resource ("disjunctive") scheduling constraint: no two tasks may overlap. Task
 * {@code i} executes during {@code [start[i], start[i] + duration[i])}. Equivalent to
 * {@link CumulativeConstraint} with every resource requirement and the capacity {@code limit}
 * fixed to {@code 1} — but implemented with a strictly stronger propagator: {@link
 * CumulativeConstraint#propagate} only performs timetabling (compulsory-part overlap detection),
 * which contributes nothing until tasks' domains have already narrowed enough for compulsory
 * parts to exist. This class additionally implements <b>edge-finding</b> (Carlier &amp; Pinson
 * 1989; Baptiste, Le Pape &amp; Nuijten 2001; Vilim 2007), which reasons about groups of tasks'
 * combined time windows rather than individual compulsory parts, and so can tighten bounds even
 * when no task has a compulsory part at all — see {@link #edgeFind}'s own Javadoc for the worked
 * example.
 * <p>
 * Discrete ({@link io.github.rcrida.jcsp.domains.IntRangeDomain}) start-time variables only —
 * unlike {@link CumulativeConstraint}, this has no continuous ({@link
 * io.github.rcrida.jcsp.domains.IntervalDomain}) sibling overload: with no {@code limit}/{@code
 * resources} parameter to disambiguate on (fixed at 1), an {@code Integer}/{@code Double} pair of
 * {@code of(...)} overloads would erase to identical signatures (JLS 8.4.2), the same pitfall
 * {@link LinearBooleanBoundConstraint}/{@link io.github.rcrida.jcsp.constraints.nary.DiffnVariableConstraint}
 * were named apart to avoid. No real use case in this codebase needs continuous disjunctive
 * scheduling; {@code cumulativeConstraint(..., 1)} remains available (with weaker propagation)
 * for anyone who does.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DisjunctiveConstraint extends NaryConstraint implements Propagatable {
    @Singular("start")    private final List<Variable<Integer>> starts;
    @Singular("duration") private final List<Integer> durations;

    public static DisjunctiveConstraint of(@NonNull List<Variable<Integer>> starts,
                                           @NonNull List<Integer> durations) {
        assert starts.size() == durations.size() : "starts and durations must have equal length";
        var b = DisjunctiveConstraint.builder();
        starts.forEach(v -> b.variable(v).start(v));
        durations.forEach(b::duration);
        return b.build();
    }

    /**
     * Sorts assigned tasks by start time and checks only consecutive pairs for overlap — sufficient
     * to detect any overlap, not just adjacent ones: if two non-adjacent intervals in start-sorted
     * order overlapped, the interval immediately after the first would necessarily start before the
     * first's end too, so some adjacent pair would already have failed the check.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = starts.size();
        List<int[]> intervals = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Optional<Integer> sv = assignment.getValue(starts.get(i));
            if (sv.isEmpty()) return true; // optimistic for partial assignments
            int s = sv.get();
            intervals.add(new int[]{s, s + durations.get(i)});
        }
        intervals.sort(Comparator.comparingInt(iv -> iv[0]));
        for (int i = 1; i < intervals.size(); i++) {
            if (intervals.get(i)[0] < intervals.get(i - 1)[1]) return false;
        }
        return true;
    }

    /**
     * Runs {@link #edgeFind} on the current bounds (tightening {@code est}), then again on the
     * time-reversed instance (tightening {@code lst} — see {@link #edgeFind}'s own Javadoc for the
     * reversal mapping), combines both results with the original bounds, and narrows via {@link
     * NumericBounds#narrow}. A task whose combined {@code [est, lst]} window becomes empty (rather
     * than either pass detecting an overload on its own) signals infeasibility from the
     * <em>interaction</em> of the two passes.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        int[] est = new int[n];
        int[] lst = new int[n];
        int[] dur = new int[n];
        int[] lct = new int[n];
        for (int i = 0; i < n; i++) {
            Domain<Integer> dom = (Domain<Integer>) domains.get(starts.get(i));
            est[i] = (int) NumericBounds.min(dom);
            lst[i] = (int) NumericBounds.max(dom);
            dur[i] = durations.get(i);
            lct[i] = lst[i] + dur[i];
        }

        EdgeFindResult forward = edgeFind(est, lct, dur);
        if (forward.infeasible()) return Optional.empty();

        int[] revEst = new int[n];
        int[] revLct = new int[n];
        for (int i = 0; i < n; i++) {
            revEst[i] = -lct[i];
            revLct[i] = -est[i];
        }
        int[] backwardNewEst = tightenedEst(revEst, revLct, dur);

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int newEst = Math.max(est[i], forward.newEst()[i]);
            int newLst = Math.min(lst[i], -backwardNewEst[i] - dur[i]);
            if (newEst > newLst) return Optional.empty();
            if (newEst != est[i] || newLst != lst[i]) {
                Variable<Integer> variable = starts.get(i);
                Domain<Integer> dom = (Domain<Integer>) domains.get(variable);
                NumericBounds.<Integer>narrow(dom, newEst, newLst).ifPresent(d -> updated.put(variable, d));
            }
        }
        return Optional.of(updated);
    }

    /**
     * Re-derives the same two {@link #edgeFind} passes {@link #propagate} did (no state threaded
     * between the two calls, matching {@link CumulativeConstraint#explainInfeasible}'s own
     * from-scratch re-derivation): if either pass itself reported an overloaded task set, that set
     * is the culprit. Otherwise re-checks the same combined {@code [est, lst]} window per task
     * {@link #propagate} computes — if some task's combined {@code est > lst} (the two passes'
     * bounds interacting rather than either alone overloading), cites every task in this constraint
     * rather than tracking which sub-groups each pass's bound came from, a deliberately simpler
     * (still sound, only less tight) fallback. If neither condition holds (this constraint is
     * actually feasible against {@code domains} — a real case, not just a defensive check: this
     * method must be safe to call standalone, not only after {@link #propagate} itself returned
     * infeasible), returns {@link Optional#empty()}. Either way a culprit set is cited,
     * {@link RangeNogoodConstraint#fromCurrentBounds} is tried first (sound without requiring any
     * cited task to be singleton), falling back to {@link GroundNogoodConstraint} via {@link
     * Propagatable#allSingletonReason} only when a cited domain can't be soundly cited as a range.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        int[] est = new int[n];
        int[] lst = new int[n];
        int[] dur = new int[n];
        int[] lct = new int[n];
        for (int i = 0; i < n; i++) {
            Domain<Integer> dom = (Domain<Integer>) domains.get(starts.get(i));
            est[i] = (int) NumericBounds.min(dom);
            lst[i] = (int) NumericBounds.max(dom);
            dur[i] = durations.get(i);
            lct[i] = lst[i] + dur[i];
        }

        EdgeFindResult forward = edgeFind(est, lct, dur);
        if (forward.infeasible()) return citeCulprits(indicesToVariables(forward.culprits()), domains);

        int[] revEst = new int[n];
        int[] revLct = new int[n];
        for (int i = 0; i < n; i++) {
            revEst[i] = -lct[i];
            revLct[i] = -est[i];
        }
        int[] backwardNewEst = tightenedEst(revEst, revLct, dur);

        for (int i = 0; i < n; i++) {
            int newEst = Math.max(est[i], forward.newEst()[i]);
            int newLst = Math.min(lst[i], -backwardNewEst[i] - dur[i]);
            if (newEst > newLst) return citeCulprits(getVariables(), domains);
        }
        return Optional.empty();
    }

    /**
     * Runs {@link #edgeFind} and returns its tightened {@code est} array directly, relying on it
     * never reporting infeasible. Valid only once a <em>separate</em> {@link #edgeFind} call (the
     * forward-direction one, in both {@link #propagate} and {@link #explainInfeasible}) has already
     * confirmed no task-interval is overloaded: since the overload rule (as derived in {@link
     * #edgeFind}'s own Javadoc) is symmetric under the same time-reversal used to get from forward
     * to backward — {@code minEst(Θ) + p(Θ) > maxLct(Θ)} names the exact same real quantities either
     * way — a direction that already ran clean guarantees the other direction's own overload check
     * can never trigger either. Not asserted (unlike this class's caller-facing {@link #of}
     * precondition): this is an internal algorithmic invariant established by the derivation above,
     * not a contract a caller could violate, the same posture {@link CumulativeConstraint}'s own
     * internal helpers take toward properties their own Javadoc argues for rather than checks at
     * runtime. Named for what it's used for (the backward/{@code lst}-tightening pass) rather than
     * being a second, differently-scoped copy of {@link #edgeFind} itself.
     */
    private static int[] tightenedEst(int[] est, int[] lct, int[] dur) {
        return edgeFind(est, lct, dur).newEst();
    }

    private Set<Variable<?>> indicesToVariables(Set<Integer> indices) {
        Set<Variable<?>> vars = new LinkedHashSet<>();
        for (int idx : indices) vars.add(starts.get(idx));
        return vars;
    }

    private static Optional<NogoodConstraint> citeCulprits(Collection<? extends Variable<?>> culprits,
                                                            Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(culprits, domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(culprits, domains)));
    }

    /** The result of one {@link #edgeFind} sweep: either an infeasible task-index set, or tightened {@code est} values. */
    private record EdgeFindResult(boolean infeasible, int[] newEst, Set<Integer> culprits) {
        static EdgeFindResult infeasible(Set<Integer> culprits) { return new EdgeFindResult(true, null, culprits); }
        static EdgeFindResult feasible(int[] newEst) { return new EdgeFindResult(false, newEst, null); }
    }

    /**
     * Direct task-interval enumeration edge-finding (Vilim 2007's "Edge-Finding Rule", O(n³)
     * worst case — not the fully-optimized O(n log n) Θ-Λ-tree algorithm; see this class's own
     * top-level Javadoc reference list). For a task set {@code Θ}, define {@code est(Θ)} = min
     * {@code est} over {@code Θ}, {@code lct(Θ)} = max {@code lct} over {@code Θ}, {@code p(Θ)} =
     * sum of durations over {@code Θ}.
     * <p>
     * <b>Overload rule</b>: if {@code est(Θ) + p(Θ) > lct(Θ)} for any {@code Θ}, infeasible — {@code
     * Θ}'s own tasks can't fit in their combined window.
     * <p>
     * <b>Edge-finding rule</b>: for {@code Θ} and task {@code j ∉ Θ} with {@code est_j ≥ est(Θ)}, if
     * {@code est(Θ) + p(Θ) + p_j > lct(Θ)}, then {@code j} cannot finish by {@code lct(Θ)} alongside
     * every task of {@code Θ}, forcing {@code est_j ← max(est_j, est(Θ) + p(Θ))}.
     * <p>
     * Only "task-interval" {@code Θ}s — of the form {@code {k : est_k ≥ est_i, lct_k ≤ lct_l}} for
     * some pair of tasks {@code i, l} — can ever produce the tightest bound (a standard result in
     * the edge-finding literature), so it suffices to enumerate {@code O(n²)} such sets rather than
     * all {@code 2ⁿ} subsets: the outer loop fixes an {@code est} threshold (one of the {@code n}
     * tasks' own {@code est} values), the inner loop grows {@code Θ} by increasing {@code lct} among
     * tasks meeting that threshold. The threshold only <em>restricts the candidate set</em> though —
     * the actual {@code est(Θ)}/{@code lct(Θ)} used in both rules above is {@code Θ}'s own running
     * min/max as it grows, not the threshold itself (using the threshold directly there would be
     * unsound whenever {@code Θ} doesn't yet contain the task that defined it).
     * <p>
     * <b>Worked example</b> (edge-finding tightening a bound timetabling alone never could, since no
     * task here has a compulsory part at all): three duration-3 tasks, task 1 and 2 both with window
     * {@code [0,6)}, task 3 with window {@code [0,9)}. {@code Θ={1,2}}: {@code est(Θ)=0, p(Θ)=6,
     * lct(Θ)=6} — not overloaded alone. But task 3 has {@code est_3=0 ≥ est(Θ)=0}, and
     * {@code est(Θ)+p(Θ)+p_3 = 9 > lct(Θ)=6}, forcing {@code est_3 ← max(0, 0+6) = 6}.
     * <p>
     * Called twice by {@link #propagate}/{@link #explainInfeasible} — once directly (tightening
     * {@code est}), once on a time-reversed instance (tightening {@code lst}): reversing lets the
     * same forward rule derive the mirror "{@code j} must finish before {@code Θ} starts" bound
     * without a second, separately-derived algorithm.
     *
     * @param est current earliest-start times
     * @param lct current latest-completion times ({@code lst + duration})
     * @param dur fixed task durations
     */
    private static EdgeFindResult edgeFind(int[] est, int[] lct, int[] dur) {
        int n = est.length;
        int[] newEst = est.clone();
        Integer[] estOrder = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(estOrder, Comparator.comparingInt(i -> est[i]));

        for (int ii = 0; ii < n; ii++) {
            int threshold = est[estOrder[ii]];
            List<Integer> candidates = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                if (est[k] >= threshold) candidates.add(k);
            }
            candidates.sort(Comparator.comparingInt(k -> lct[k]));

            int sumP = 0;
            int maxLct = Integer.MIN_VALUE;
            int minEst = Integer.MAX_VALUE;
            boolean[] inTheta = new boolean[n];
            List<Integer> thetaSoFar = new ArrayList<>();
            for (int k : candidates) {
                sumP += dur[k];
                maxLct = Math.max(maxLct, lct[k]);
                minEst = Math.min(minEst, est[k]);
                inTheta[k] = true;
                thetaSoFar.add(k);

                if (minEst + sumP > maxLct) {
                    return EdgeFindResult.infeasible(new LinkedHashSet<>(thetaSoFar));
                }

                for (int j = 0; j < n; j++) {
                    if (inTheta[j] || est[j] < minEst) continue;
                    if (minEst + sumP + dur[j] > maxLct) {
                        newEst[j] = Math.max(newEst[j], minEst + sumP);
                    }
                }
            }
        }
        return EdgeFindResult.feasible(newEst);
    }

    @Override
    public String getRelation() {
        return "disjunctive(tasks=" + starts.size() + ")";
    }
}
