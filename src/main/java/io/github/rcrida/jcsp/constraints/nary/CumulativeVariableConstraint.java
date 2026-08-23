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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The variable-duration/variable-resource sibling of {@link CumulativeConstraint}: {@link
 * #durations} and {@link #resources} are genuine decision variables rather than fixed constants
 * (same erasure-collision naming pattern as {@link DiffnVariableConstraint} vs {@link
 * DiffnConstraint} -- {@code List<Double>} and {@code List<Variable<?>>} erase to the same raw
 * {@code List}, so a same-named overload of {@link CumulativeConstraint#of} isn't possible).
 * <p>
 * Runs the same two checks {@link CumulativeConstraint} does -- timetabling (compulsory parts)
 * and an energy-overload task-interval check -- both generalised to use each task's
 * <em>current domain minimum</em> duration/resource as its guaranteed floor, the same convention
 * {@link DiffnPropagation}'s {@code SizeLookup} already uses for variable-sized rectangles: a
 * task's true duration/resource can never be smaller than its domain minimum, so substituting the
 * minimum everywhere a fixed value would otherwise appear is sound (a conservative underestimate
 * only ever makes an overload conclusion <em>more</em> certain, never less), if not always
 * maximally tight. Like {@link DiffnVariableConstraint}, {@link #propagate} never narrows the
 * duration/resource variables themselves, only the start-time origins -- narrowing sizes from
 * cumulative reasoning would need different (currently unimplemented) math.
 * <p>
 * {@link #explainInfeasible}'s citation sets always include the contributing duration/resource
 * variables alongside their origins, not just the origins: omitting them would be unsound, not
 * merely imprecise. A nogood derived from one search branch's current {@code duration}/{@code
 * resource} domain minimums is checked globally across the whole search tree, including branches
 * whose {@code duration}/{@code resource} domains bear no narrowing relationship to this branch's
 * at all -- so a citation that doesn't pin those variables down could misfire against a branch
 * where the true duration/resource is smaller than what was used to derive this conflict, the same
 * reasoning {@link DiffnVariableConstraint} already documents for its own width/height variables.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CumulativeVariableConstraint extends NaryConstraint implements Propagatable {
    @Singular("start")    private final List<Variable<?>> starts;
    @Singular("duration") private final List<Variable<?>> durations;
    @Singular("resource") private final List<Variable<?>> resources;
    private final double limit;

    public static CumulativeVariableConstraint of(@NonNull List<Variable<?>> starts,
                                                    @NonNull List<Variable<?>> durations,
                                                    @NonNull List<Variable<?>> resources,
                                                    double limit) {
        assert starts.size() == durations.size() && starts.size() == resources.size()
                : "starts, durations and resources must have equal length";
        var b = CumulativeVariableConstraint.builder().limit(limit);
        for (int i = 0; i < starts.size(); i++) {
            b.variable(starts.get(i)).start(starts.get(i));
            b.variable(durations.get(i)).duration(durations.get(i));
            b.variable(resources.get(i)).resource(resources.get(i));
        }
        return b.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = starts.size();
        List<double[]> events = new ArrayList<>(2 * n);
        for (int i = 0; i < n; i++) {
            var sv = assignment.getValue((Variable<Number>) starts.get(i));
            var dv = assignment.getValue((Variable<Number>) durations.get(i));
            var rv = assignment.getValue((Variable<Number>) resources.get(i));
            if (sv.isEmpty() || dv.isEmpty() || rv.isEmpty()) return true; // optimistic for partial assignments
            double s = sv.get().doubleValue();
            double d = dv.get().doubleValue();
            double r = rv.get().doubleValue();
            events.add(new double[]{s,     +r});
            events.add(new double[]{s + d, -r});
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

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        double[] est = new double[n];
        double[] lst = new double[n];
        double[] dmin = new double[n];
        double[] rmin = new double[n];
        for (int i = 0; i < n; i++) {
            var sdom = (Domain<Number>) domains.get(starts.get(i));
            est[i] = NumericBounds.min(sdom);
            lst[i] = NumericBounds.max(sdom);
            dmin[i] = NumericBounds.min((Domain<Number>) domains.get(durations.get(i)));
            rmin[i] = NumericBounds.min((Domain<Number>) domains.get(resources.get(i)));
        }
        List<double[]> events = buildEvents(est, lst, dmin, rmin);

        // Global overload check (timetabling: compulsory-part events only)
        double running = 0;
        for (double[] e : events) {
            running += e[1];
            if (running > limit) return Optional.empty();
        }

        // Energy overload check -- see class Javadoc for the domain-minimum soundness argument.
        double[] lct = new double[n];
        for (int i = 0; i < n; i++) lct[i] = lst[i] + dmin[i];
        if (energyOverload(est, lct, dmin, rmin).isPresent()) return Optional.empty();

        // Tighten each task's start window (never the duration/resource variables themselves)
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            var window = taskWindow(i, est, lst, dmin, rmin, events);
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
     * As {@link CumulativeConstraint#buildEvents}, but using each task's current domain-minimum
     * duration/resource ({@code dmin}/{@code rmin}) rather than a fixed constant.
     */
    private List<double[]> buildEvents(double[] est, double[] lst, double[] dmin, double[] rmin) {
        List<double[]> events = new ArrayList<>();
        for (int i = 0; i < est.length; i++) {
            double compEnd = est[i] + dmin[i];
            if (lst[i] < compEnd) {
                events.add(new double[]{lst[i],  +rmin[i], i});
                events.add(new double[]{compEnd, -rmin[i], i});
            }
        }
        events.sort((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Double.compare(a[1], b[1]);
        });
        return events;
    }

    /** The tightened start-time window computed for one task, or {@code feasible=false} when none exists. */
    private record TaskWindow(boolean feasible, double newEst, double newLst) {}

    /**
     * As {@link CumulativeConstraint#energyOverload}, generalised to {@code dmin}/{@code rmin}
     * arrays instead of fixed per-task constants -- see this class's own Javadoc for why that
     * substitution is sound. Same deliberate scope limit as the fixed-size version: overload
     * detection only, not the bound-tightening half of full cumulative edge-finding.
     */
    private Optional<Set<Integer>> energyOverload(double[] est, double[] lct, double[] dmin, double[] rmin) {
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
                sumEnergy += dmin[k] * rmin[k];
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

    /** As {@link CumulativeConstraint#taskWindow}, using task {@code i}'s own {@code dmin}/{@code rmin}. */
    private TaskWindow taskWindow(int i, double[] est, double[] lst, double[] dmin, double[] rmin, List<double[]> events) {
        double di = dmin[i];
        double ri = rmin[i];
        double slack = limit - ri;

        List<double[]> exEvents = new ArrayList<>(events.size());
        for (double[] e : events) {
            if ((int) e[2] != i) exEvents.add(e);
        }

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
            overloaded.add(new double[]{overloadStart, Double.POSITIVE_INFINITY});
        }

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
     * As {@link CumulativeConstraint#explainInfeasible}, but every citation set additionally
     * includes each contributing task's duration and resource variables alongside its start
     * variable -- see this class's own Javadoc for why that's required for soundness here, not
     * merely for symmetry with the fixed-size version.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        double[] est = new double[n];
        double[] lst = new double[n];
        double[] dmin = new double[n];
        double[] rmin = new double[n];
        for (int i = 0; i < n; i++) {
            var sdom = (Domain<Number>) domains.get(starts.get(i));
            est[i] = NumericBounds.min(sdom);
            lst[i] = NumericBounds.max(sdom);
            dmin[i] = NumericBounds.min((Domain<Number>) domains.get(durations.get(i)));
            rmin[i] = NumericBounds.min((Domain<Number>) domains.get(resources.get(i)));
        }
        List<double[]> events = buildEvents(est, lst, dmin, rmin);

        Set<Variable<?>> compulsoryVars = new LinkedHashSet<>();
        for (double[] e : events) {
            int idx = (int) e[2];
            compulsoryVars.add(starts.get(idx));
            compulsoryVars.add(durations.get(idx));
            compulsoryVars.add(resources.get(idx));
        }

        double running = 0;
        for (double[] e : events) {
            running += e[1];
            if (running > limit) {
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(compulsoryVars, domains));
            }
        }

        double[] lct = new double[n];
        for (int i = 0; i < n; i++) lct[i] = lst[i] + dmin[i];
        Optional<Set<Integer>> overloadedTheta = energyOverload(est, lct, dmin, rmin);
        if (overloadedTheta.isPresent()) {
            Set<Variable<?>> culprits = new LinkedHashSet<>();
            for (int idx : overloadedTheta.get()) {
                culprits.add(starts.get(idx));
                culprits.add(durations.get(idx));
                culprits.add(resources.get(idx));
            }
            return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(culprits, domains));
        }

        for (int i = 0; i < n; i++) {
            if (!taskWindow(i, est, lst, dmin, rmin, events).feasible()) {
                Set<Variable<?>> culprits = new LinkedHashSet<>(compulsoryVars);
                culprits.add(starts.get(i));
                culprits.add(durations.get(i));
                culprits.add(resources.get(i));
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(culprits, domains));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "cumulativeVariable(tasks=" + starts.size() + ")";
    }
}
