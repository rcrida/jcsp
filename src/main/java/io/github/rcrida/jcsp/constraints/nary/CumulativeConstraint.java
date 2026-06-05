package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An n-ary constraint that bounds resource usage over time: at every instant, the sum of
 * resources consumed by concurrently executing tasks must not exceed {@code limit}.
 * Task {@code i} executes during {@code [start[i], start[i] + duration[i])}.
 * <p>
 * Beyond the basic satisfiability check, {@link #propagate} implements a timetabling
 * propagator: it identifies each task's <em>compulsory part</em> (the interval that must
 * be occupied regardless of the final start time), builds a mandatory-resource profile,
 * and tightens each start-time domain to exclude positions that would exceed the capacity.
 * <p>
 * Equivalent to MiniZinc's {@code cumulative(start, duration, resource, limit)} constraint.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CumulativeConstraint extends NaryConstraint implements Propagatable {
    @Singular private final List<Variable<Integer>> starts;
    @Singular private final List<Integer> durations;
    @Singular private final List<Integer> resources;
    private final int limit;

    public static CumulativeConstraint of(@NonNull List<Variable<Integer>> starts,
                                          @NonNull List<Integer> durations,
                                          @NonNull List<Integer> resources,
                                          int limit) {
        assert starts.size() == durations.size() && starts.size() == resources.size()
                : "starts, durations and resources must have equal length";
        return CumulativeConstraint.builder()
                .variables(starts)
                .starts(starts)
                .durations(durations)
                .resources(resources)
                .limit(limit)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = starts.size();
        List<int[]> events = new ArrayList<>(2 * n);
        for (int i = 0; i < n; i++) {
            Optional<Integer> sv = assignment.getValue(starts.get(i));
            if (sv.isEmpty()) return true; // optimistic for partial assignments
            int s = sv.get();
            events.add(new int[]{s,                    +resources.get(i)});
            events.add(new int[]{s + durations.get(i), -resources.get(i)});
        }
        events.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int running = 0;
        for (int[] e : events) {
            running += e[1];
            if (running > limit) return false;
        }
        return true;
    }

    /**
     * Timetabling propagator. Computes mandatory resource usage (compulsory parts) and
     * tightens each start variable's domain to remove positions that would cause overload.
     *
     * @param domains current variable domains
     * @return updated domains for start variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is infeasible
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public Optional<Map<Variable<?>, Domain<?>>> propagate(
            @NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = starts.size();
        int[] est = new int[n]; // earliest start
        int[] lst = new int[n]; // latest start

        for (int i = 0; i < n; i++) {
            var dom = (Domain<Integer>) domains.get(starts.get(i));
            est[i] = dom.stream().mapToInt(v -> v).min().orElseThrow();
            lst[i] = dom.stream().mapToInt(v -> v).max().orElseThrow();
        }

        int minTime = Integer.MAX_VALUE, maxTime = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            minTime = Math.min(minTime, est[i]);
            maxTime = Math.max(maxTime, lst[i] + durations.get(i));
        }
        if (minTime >= maxTime) return Optional.of(Map.of());

        int horizon = maxTime - minTime;
        int[] profile = new int[horizon];

        // Build full mandatory profile from compulsory parts
        for (int i = 0; i < n; i++) {
            // Compulsory part: [lst[i], est[i]+duration[i]) — non-empty when lst[i] < est[i]+d[i]
            int compStart = lst[i];
            int compEnd   = est[i] + durations.get(i);
            for (int t = compStart; t < compEnd; t++) {
                profile[t - minTime] += resources.get(i);
            }
        }

        // Overload check
        for (int t = 0; t < horizon; t++) {
            if (profile[t] > limit) return Optional.empty();
        }

        // Tighten each task's start window
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            // Exclusive profile: remove task i's compulsory contribution
            int[] ex = profile.clone();
            int compStart = lst[i];
            int compEnd   = est[i] + durations.get(i);
            for (int t = compStart; t < compEnd; t++) {
                ex[t - minTime] -= resources.get(i);
            }

            int ri = resources.get(i), di = durations.get(i);

            // New earliest start: first s ∈ [est[i], lst[i]] where no t in [s,s+d) has ex[t]+ri > limit
            int newEst = est[i];
            while (newEst <= lst[i]) {
                boolean blocked = false;
                for (int t = newEst; t < newEst + di; t++) {
                    if (ex[t - minTime] + ri > limit) { blocked = true; break; }
                }
                if (!blocked) break;
                newEst++;
            }
            if (newEst > lst[i]) return Optional.empty();

            // New latest start: last s ∈ [newEst, lst[i]] where no t in [s,s+d) has ex[t]+ri > limit.
            // newEst is always feasible, so newLst will never go below newEst.
            int newLst = lst[i];
            while (newLst > newEst) {
                boolean blocked = false;
                for (int t = newLst; t < newLst + di; t++) {
                    if (ex[t - minTime] + ri > limit) { blocked = true; break; }
                }
                if (!blocked) break;
                newLst--;
            }

            if (newEst != est[i] || newLst != lst[i]) {
                updated.put(starts.get(i), IntRangeDomain.of(newEst, newLst));
            }
        }
        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "cumulative(limit=" + limit + ", tasks=" + starts.size() + ")";
    }
}
