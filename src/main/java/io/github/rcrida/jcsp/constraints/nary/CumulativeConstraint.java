package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * An n-ary constraint that bounds resource usage over time: at every instant, the sum of
 * resources consumed by concurrently executing tasks must not exceed {@link #limit}.
 * Task {@code i} executes during {@code [start[i], start[i] + duration[i])}.
 * <p>
 * Start-time variables may be backed by either {@link io.github.rcrida.jcsp.domains.IntRangeDomain}
 * (integer scheduling) or {@link IntervalDomain} (continuous scheduling). Durations, resources,
 * and the capacity limit are stored as {@code double} in both cases.
 * <p>
 * Beyond the basic satisfiability check, {@link #propagate} implements a timetabling
 * propagator: it identifies each task's <em>compulsory part</em> (the interval that must
 * be occupied regardless of the final start time), builds a mandatory-resource profile via
 * an event-based step function, and tightens each start-time domain to exclude positions
 * that would exceed the capacity. It additionally runs {@link #energyOverload}, an
 * energy-weighted overload check (a real strengthening: it catches a group of tasks that
 * collectively overload a shared window even when no <em>pair</em> of their compulsory parts
 * individually conflicts) — see that method's own Javadoc for exactly what it does and
 * doesn't cover relative to full cumulative edge-finding.
 * <p>
 * Equivalent to MiniZinc's {@code cumulative(start, duration, resource, limit)} constraint.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CumulativeConstraint extends NaryConstraint implements Propagatable {
    @Singular("start")    private final List<Variable<?>> starts;
    @Singular("duration") private final List<Double> durations;
    @Singular("resource") private final List<Double> resources;
    private final double limit;

    /** Integer overload — backward-compatible with existing call sites. */
    public static CumulativeConstraint of(@NonNull List<Variable<Integer>> starts,
                                          @NonNull List<Integer> durations,
                                          @NonNull List<Integer> resources,
                                          int limit) {
        assert starts.size() == durations.size() && starts.size() == resources.size()
                : "starts, durations and resources must have equal length";
        var b = CumulativeConstraint.builder().limit(limit);
        starts.forEach(v -> b.variable(v).start(v));
        durations.forEach(d -> b.duration(d.doubleValue()));
        resources.forEach(r -> b.resource(r.doubleValue()));
        return b.build();
    }

    /** Double overload — for {@link IntervalDomain} start variables. */
    public static CumulativeConstraint of(@NonNull List<Variable<Double>> starts,
                                          @NonNull List<Double> durations,
                                          @NonNull List<Double> resources,
                                          double limit) {
        assert starts.size() == durations.size() && starts.size() == resources.size()
                : "starts, durations and resources must have equal length";
        var b = CumulativeConstraint.builder().limit(limit);
        starts.forEach(v -> b.variable(v).start(v));
        durations.forEach(b::duration);
        resources.forEach(b::resource);
        return b.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = starts.size();
        List<double[]> events = new ArrayList<>(2 * n);
        for (int i = 0; i < n; i++) {
            var sv = assignment.getValue((Variable<Number>) (Variable<?>) starts.get(i));
            if (sv.isEmpty()) return true; // optimistic for partial assignments
            double s = sv.get().doubleValue();
            events.add(new double[]{s,                         +resources.get(i)});
            events.add(new double[]{s + durations.get(i),     -resources.get(i)});
        }
        events.sort((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Double.compare(a[1], b[1]); // releases before claims at same time
        });
        double running = 0;
        for (double[] e : events) {
            running += e[1];
            if (running > limit) return false;
        }
        return true;
    }

    /**
     * Event-based timetabling propagator. Computes mandatory resource usage (compulsory parts),
     * finds overloaded intervals in each task's exclusive profile, and tightens each start-time
     * domain to exclude positions that would cause overload.
     * <p>
     * Works for both integer ({@link IntRangeDomain}) and continuous ({@link IntervalDomain})
     * start variables. For integer domains the algorithm is equivalent to the classic array-based
     * approach; for continuous domains it uses exact double arithmetic throughout.
     *
     * @param domains current variable domains
     * @return updated domains for start variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is infeasible
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(
            @NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        double[] est = new double[n];
        double[] lst = new double[n];
        for (int i = 0; i < n; i++) {
            var dom = (Domain<Number>) (Domain<?>) domains.get(starts.get(i));
            est[i] = NumericBounds.min(dom);
            lst[i] = NumericBounds.max(dom);
        }
        List<double[]> events = buildEvents(est, lst);

        // Global overload check (timetabling: compulsory-part events only)
        double running = 0;
        for (double[] e : events) {
            running += e[1];
            if (running > limit) return Optional.empty();
        }

        // Energy overload check: catches collective overloads across a group of tasks that no
        // pair's compulsory parts individually conflict on -- see energyOverload's own Javadoc.
        double[] lct = new double[n];
        for (int i = 0; i < n; i++) lct[i] = lst[i] + durations.get(i);
        if (energyOverload(est, lct).isPresent()) return Optional.empty();

        // Tighten each task's start window
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            var window = taskWindow(i, est, lst, events);
            if (!window.feasible()) return Optional.empty();
            double newEst = window.newEst();
            double newLst = window.newLst();
            if (newEst != est[i] || newLst != lst[i]) {
                var dom = domains.get(starts.get(i));
                if (dom instanceof BoundedDomain<?>) {
                    updated.put(starts.get(i), IntervalDomain.of(newEst, newLst));
                } else {
                    updated.put(starts.get(i), IntRangeDomain.of((int) newEst, (int) newLst));
                }
            }
        }
        return Optional.of(updated);
    }

    /**
     * Builds compulsory-part events {@code [time, delta_resource, task_index]}, sorted with
     * releases before claims at the same time. Task {@code i} has a compulsory part
     * {@code [lst[i], est[i]+d[i])} — and so contributes events — only when
     * {@code lst[i] < est[i]+d[i]}.
     */
    private List<double[]> buildEvents(double[] est, double[] lst) {
        List<double[]> events = new ArrayList<>();
        for (int i = 0; i < est.length; i++) {
            double compEnd = est[i] + durations.get(i);
            if (lst[i] < compEnd) {
                events.add(new double[]{lst[i],  +resources.get(i), i});
                events.add(new double[]{compEnd, -resources.get(i), i});
            }
        }
        events.sort((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Double.compare(a[1], b[1]); // releases before claims at same time
        });
        return events;
    }

    /**
     * Direct task-interval enumeration of the cumulative <em>overload</em> rule -- the
     * energy-weighted generalisation of {@link DisjunctiveConstraint#edgeFind}'s overload check,
     * not its bound-tightening edge-finding rule (see below for why that half is deliberately not
     * attempted here). For a task set {@code Θ} built from tasks whose current bounds place them
     * entirely inside a shared window (every {@code k∈Θ} has {@code est[k] >= est(Θ)} and
     * {@code lct[k] <= lct(Θ)}, where {@code est(Θ)}/{@code lct(Θ)} are Θ's own running min/max as
     * it grows), each such task is necessarily scheduled entirely within {@code [est(Θ), lct(Θ))}
     * regardless of where exactly within its own window it lands. Θ's tasks therefore collectively
     * require {@code energy(Θ) = Σ duration[k]*resource[k]} somewhere inside a window of capacity
     * {@code limit*(lct(Θ)-est(Θ))} -- exceeding it is a direct infeasibility. This is a real
     * strengthening over {@link #propagate}'s existing timetabling check: it catches a group of
     * tasks that collectively overload a shared window even when no <em>pair</em> of their
     * compulsory parts individually conflicts (timetabling's own blind spot). Reduces exactly to
     * {@link DisjunctiveConstraint#edgeFind}'s overload rule when every {@code resource} and
     * {@code limit} is {@code 1}.
     * <p>
     * Same {@code O(n³)} task-interval enumeration style as {@link DisjunctiveConstraint#edgeFind}
     * (not the optimised {@code O(n)}/{@code O(n log n)} sweep formulations in the literature —
     * same tradeoff {@code DisjunctiveConstraint} already made, revisit only if profiling on a
     * real instance shows this enumeration itself as the bottleneck): outer loop fixes an
     * {@code est} threshold, inner loop grows {@code Θ} by increasing {@code lct} among tasks
     * meeting that threshold.
     * <p>
     * Deliberately does <em>not</em> attempt the bound-tightening half of cumulative edge-finding
     * (pushing an external task {@code j}'s {@code est} the way {@link
     * DisjunctiveConstraint#edgeFind} does for the disjunctive case): that rule's standard proof
     * relies on {@code j} either finishing entirely before {@code Θ}'s window or starting entirely
     * after it, which is exact only when no two tasks can ever coexist on the resource (the
     * disjunctive special case, {@code limit=1}) -- in general cumulative scheduling {@code j} can
     * legitimately overlap {@code Θ}'s window as long as combined usage stays under {@code limit},
     * so that argument doesn't carry over without a separately, carefully re-derived (or
     * faithfully ported, algorithm-and-all) theorem. Left as explicit future work rather than
     * risking a plausible-looking but unsound port.
     *
     * @return the task indices of an overloaded {@code Θ}, or {@link Optional#empty()} if none is found
     */
    private Optional<Set<Integer>> energyOverload(double[] est, double[] lct) {
        int n = est.length;
        Integer[] estOrder = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(estOrder, Comparator.comparingDouble(i -> est[i]));

        for (int ii = 0; ii < n; ii++) {
            double threshold = est[estOrder[ii]];
            List<Integer> candidates = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                if (est[k] >= threshold) candidates.add(k);
            }
            candidates.sort(Comparator.comparingDouble(k -> lct[k]));

            double sumEnergy = 0;
            double maxLct = Double.NEGATIVE_INFINITY;
            double minEst = Double.POSITIVE_INFINITY;
            List<Integer> thetaSoFar = new ArrayList<>();
            for (int k : candidates) {
                sumEnergy += durations.get(k) * resources.get(k);
                maxLct = Math.max(maxLct, lct[k]);
                minEst = Math.min(minEst, est[k]);
                thetaSoFar.add(k);
                if (sumEnergy > limit * (maxLct - minEst)) {
                    return Optional.of(new LinkedHashSet<>(thetaSoFar));
                }
            }
        }
        return Optional.empty();
    }

    /** The tightened start-time window computed for one task, or {@code feasible=false} when none exists. */
    private record TaskWindow(boolean feasible, double newEst, double newLst) {}

    /**
     * Builds task {@code i}'s exclusive resource profile (every other task's compulsory-part
     * events) and walks it to find the tightened {@code [newEst, newLst]} start window, exactly as
     * {@link #propagate} did inline. Returns {@code feasible=false} when the forward scan advances
     * {@code newEst} past {@code lst[i]} — no start remains that keeps task {@code i} within
     * capacity given everyone else's mandatory usage.
     */
    private TaskWindow taskWindow(int i, double[] est, double[] lst, List<double[]> events) {
        double di = durations.get(i);
        double ri = resources.get(i);
        double slack = limit - ri; // exclusive profile must not exceed this

        // Exclusive event list: all events except task i's own compulsory contribution
        List<double[]> exEvents = new ArrayList<>(events.size());
        for (double[] e : events) {
            if ((int) e[2] != i) exEvents.add(e);
        }

        // Walk exclusive profile to find intervals [a, b) where runEx > slack.
        // Initialize overload state before any events (handles slack < 0 case where ri > limit).
        List<double[]> overloaded = new ArrayList<>();
        double runEx = 0;
        double overloadStart = runEx > slack ? Double.NEGATIVE_INFINITY : Double.NaN;
        for (double[] e : exEvents) {
            boolean wasOver = runEx > slack;
            runEx += e[1];
            boolean isOver = runEx > slack;
            if (!wasOver && isOver) {
                overloadStart = e[0];
            } else if (wasOver && !isOver) {
                overloaded.add(new double[]{overloadStart, e[0]});
                overloadStart = Double.NaN;
            }
        }
        if (!Double.isNaN(overloadStart)) {
            // Profile still overloaded after all events (e.g. ri > limit)
            overloaded.add(new double[]{overloadStart, Double.POSITIVE_INFINITY});
        }

        // Task at s occupies [s, s+d). It conflicts with overloaded [a, b) iff
        // s+d > a and s < b, i.e., s > a-d and s < b → forbidden start range: (a-d, b).

        // Forward scan: find new EST by advancing past forbidden start windows
        double newEst = est[i];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (double[] ov : overloaded) {
                if (newEst > ov[0] - di && newEst < ov[1]) {
                    newEst = ov[1];
                    changed = true;
                }
            }
        }
        if (newEst > lst[i]) return new TaskWindow(false, newEst, lst[i]);

        // Backward scan: find new LST by retreating before forbidden start windows
        double newLst = lst[i];
        changed = true;
        while (changed) {
            changed = false;
            for (double[] ov : overloaded) {
                if (newLst > ov[0] - di && newLst < ov[1]) {
                    newLst = ov[0] - di;
                    changed = true;
                }
            }
        }
        return new TaskWindow(true, newEst, newLst);
    }

    /**
     * On infeasibility, rebuilds the same bounds and events as {@link #propagate} (no threading
     * needed: unlike {@link DiffnConstraint} or {@link GlobalCardinalityConstraint}, every task's
     * {@code est}/{@code lst} is read once from the given {@code domains} and never narrowed
     * mid-computation) to find the same failing check, then attributes the conflict to every task
     * whose compulsory part contributed to it, via {@link Propagatable#allSingletonReason}:
     * <ul>
     *   <li><b>Global overload</b>: every task with a non-empty compulsory part contributed an
     *       event to the running-sum check, so all of them are cited together.</li>
     *   <li><b>Energy overload</b> ({@link #energyOverload}): every task in the overloaded
     *       task-interval {@code Θ} it reports.</li>
     *   <li><b>Per-task exclusive-profile failure</b> (task {@code i} has no feasible start left):
     *       task {@code i} itself, plus every <em>other</em> task with a non-empty compulsory part
     *       (exactly the tasks whose events built the exclusive profile {@code i} was checked
     *       against).</li>
     * </ul>
     * Citing only a subset (e.g. skipping a non-singleton compulsory-part task) would be unsound:
     * that task could still narrow to a different value within its current domain and avoid the
     * conflict, so the full set must be singleton before any reason is returned — the same
     * lesson learned from {@link DiffnConstraint}'s joint mandatory-overlap condition.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        double[] est = new double[n];
        double[] lst = new double[n];
        for (int i = 0; i < n; i++) {
            var dom = (Domain<Number>) (Domain<?>) domains.get(starts.get(i));
            est[i] = NumericBounds.min(dom);
            lst[i] = NumericBounds.max(dom);
        }
        List<double[]> events = buildEvents(est, lst);

        Set<Variable<?>> compulsoryVars = new HashSet<>();
        for (double[] e : events) compulsoryVars.add(starts.get((int) e[2]));

        double running = 0;
        for (double[] e : events) {
            running += e[1];
            if (running > limit) {
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(compulsoryVars, domains));
            }
        }

        double[] lct = new double[n];
        for (int i = 0; i < n; i++) lct[i] = lst[i] + durations.get(i);
        Optional<Set<Integer>> overloadedTheta = energyOverload(est, lct);
        if (overloadedTheta.isPresent()) {
            Set<Variable<?>> culprits = new LinkedHashSet<>();
            for (int idx : overloadedTheta.get()) culprits.add(starts.get(idx));
            return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(culprits, domains));
        }

        for (int i = 0; i < n; i++) {
            if (!taskWindow(i, est, lst, events).feasible()) {
                Set<Variable<?>> culprits = new HashSet<>(compulsoryVars);
                culprits.add(starts.get(i));
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(culprits, domains));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        String limitStr = (double) (long) limit == limit
                ? String.valueOf((long) limit) : String.valueOf(limit);
        return "cumulative(limit=" + limitStr + ", tasks=" + starts.size() + ")";
    }
}
