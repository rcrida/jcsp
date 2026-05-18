package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One quarter (13 Saturdays) of parkrun scheduling with two roles per week:
 * Run Director (RD) and Volunteer Coordinator (VC).
 *
 * <p>Uses a binary-assignment formulation: one {@code Variable<Boolean>} per
 * (person, role, week) combination. Exactly-one constraints per slot, at-most-one
 * constraints for dual-role people within a week, and predicate cardinality
 * constraints enforce a fair load. The off-preference objective counts {@code true}
 * assignments in non-preferred weeks and is naturally admissible for B&B because
 * unassigned variables default to {@code false} (contributing 0).
 *
 * <p>Eight people cover all 13 weeks — three RD-only, three VC-only, and two
 * dual-role. Week 7 remains a bottleneck: every RD-eligible person except Tom is
 * unavailable that day, and Tom prefers even weeks, so week 7 is the sole
 * unavoidable off-preference assignment. Minimum off-preference count = 1.
 */
public class ParkrunSchedulingTest {
    enum Role { RD, VC }

    record Person(String name, Set<Role> roles, Set<Integer> preferredWeeks, Set<Integer> unavailableWeeks) {
        boolean canDo(Role role)      { return roles.contains(role); }
        boolean isAvailable(int week) { return !unavailableWeeks.contains(week); }
        boolean prefers(int week)     { return preferredWeeks.contains(week); }

        @Override public String toString() { return name; }
    }

    static final Solver SOLVER = Solver.Factory.INSTANCE.createSolver();
    static final int WEEKS = 8;

    static final Set<Integer> ODD_WEEKS  = IntStream.rangeClosed(1, WEEKS).filter(w -> w % 2 != 0).boxed().collect(Collectors.toSet());
    static final Set<Integer> EVEN_WEEKS = IntStream.rangeClosed(1, WEEKS).filter(w -> w % 2 == 0).boxed().collect(Collectors.toSet());
    static final Set<Integer> ALL_WEEKS  = IntStream.rangeClosed(1, WEEKS).boxed().collect(Collectors.toSet());

    static final List<Person> PEOPLE = List.of(
        // Run Directors
        new Person("Sarah", Set.of(Role.RD),            ODD_WEEKS,                    Set.of(7, 10)),
        new Person("Tom",   Set.of(Role.RD),            EVEN_WEEKS,                   Set.of(3, 13)),
        new Person("Frank", Set.of(Role.RD),            Set.of(1,2,3,4,5,6,7),       Set.of(7, 12, 13)),
        // Volunteer Coordinators
        new Person("Carol", Set.of(Role.VC),            Set.of(1,2,3,4,5,6,7),       Set.of(9, 12)),
        new Person("Dave",  Set.of(Role.VC),            Set.of(7,8,9,10,11,12,13),   Set.of(2, 5)),
        new Person("Grace", Set.of(Role.VC),            Set.of(6,7,8,9,10,11,12,13), Set.of(3, 4)),
        // Dual-role
        new Person("Eve",   Set.of(Role.RD, Role.VC),  ALL_WEEKS,                    Set.of(7, 8)),
        new Person("Henry", Set.of(Role.RD, Role.VC),  Set.of(5,6,7,8,9,10,11),     Set.of(7, 12, 13))
    );

    // Per-role slot bounds: average = WEEKS / eligible_count.
    // Each person volunteers at least ceil(average/2) and at most floor(average*2) times per role.
    static final int RD_ELIGIBLE_COUNT = (int) PEOPLE.stream().filter(p -> p.canDo(Role.RD)).count();
    static final int VC_ELIGIBLE_COUNT = (int) PEOPLE.stream().filter(p -> p.canDo(Role.VC)).count();
    static final int RD_MIN = (int) Math.ceil((double)  WEEKS / (2 * RD_ELIGIBLE_COUNT));
    static final int RD_MAX = (int) Math.floor(2.0 * WEEKS    / RD_ELIGIBLE_COUNT);
    static final int VC_MIN = (int) Math.ceil((double)  WEEKS / (2 * VC_ELIGIBLE_COUNT));
    static final int VC_MAX = (int) Math.floor(2.0 * WEEKS    / VC_ELIGIBLE_COUNT);

    /** z[person][role][week]: true iff that person fills that role that week. */
    static Map<Person, Map<Role, Map<Integer, Variable<Boolean>>>> Z;

    static final ConstraintSatisfactionProblem ROSTER = buildRoster();

    static ConstraintSatisfactionProblem buildRoster() {
        val csp = ConstraintSatisfactionProblem.builder();
        Z = new LinkedHashMap<>();

        // One boolean variable per eligible (person, role, week) combination
        for (Person p : PEOPLE) {
            Z.put(p, new LinkedHashMap<>());
            for (Role r : Role.values()) {
                if (!p.canDo(r)) continue;
                Z.get(p).put(r, new LinkedHashMap<>());
                for (int w = 1; w <= WEEKS; w++) {
                    if (!p.isAvailable(w)) continue;
                    Z.get(p).get(r).put(w, csp.createVariable(p + "_" + r + "_w" + w, BooleanDomain.INSTANCE));
                }
            }
        }

        // Each (role, week) slot must be filled by exactly one person
        for (Role r : Role.values()) {
            for (int w = 1; w <= WEEKS; w++) {
                final int week = w;
                var slotVars = slotVars(r, week);
                csp.atMostOneConstraint(slotVars);
                csp.predicateConstraint(slotVars, a ->
                    slotVars.stream().mapToInt(v -> isTrue(a, v) ? 1 : 0).sum() == 1);
            }
        }

        // A dual-role person cannot hold both roles in the same week
        for (Person p : PEOPLE) {
            if (!p.canDo(Role.RD) || !p.canDo(Role.VC)) continue;
            for (int w = 1; w <= WEEKS; w++) {
                var rdVar = Z.get(p).get(Role.RD).get(w);
                var vcVar = Z.get(p).get(Role.VC).get(w);
                if (rdVar != null && vcVar != null)
                    csp.atMostOneConstraint(Set.of(rdVar, vcVar));
            }
        }

        // Each eligible person must volunteer within [min, max] times per role
        for (Person p : PEOPLE) {
            for (Role r : Role.values()) {
                if (!p.canDo(r)) continue;
                var personRoleVars = List.copyOf(Z.get(p).get(r).values());
                int min = r == Role.RD ? RD_MIN : VC_MIN;
                int max = r == Role.RD ? RD_MAX : VC_MAX;
                csp.predicateConstraint(Set.copyOf(personRoleVars), a -> {
                    long n = personRoleVars.stream().filter(v -> isTrue(a, v)).count();
                    return n >= min && n <= max;
                });
            }
        }

        return csp.build();
    }

    static Set<Variable<Boolean>> slotVars(Role role, int week) {
        return PEOPLE.stream()
            .filter(p -> p.canDo(role))
            .map(p -> Z.get(p).get(role).get(week))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    static boolean isTrue(Assignment a, Variable<Boolean> v) {
        return Boolean.TRUE.equals(a.getValue(v).orElse(false));
    }

    // off-preference: count true variables assigned to non-preferred weeks.
    // Admissible for B&B — unassigned variables default to false (cost 0).
    static double offPreferenceCost(Assignment a) {
        return Z.entrySet().stream().mapToDouble(pe -> {
            Person p = pe.getKey();
            return pe.getValue().values().stream().mapToDouble(weekMap ->
                weekMap.entrySet().stream()
                    .filter(we -> isTrue(a, we.getValue()) && !p.prefers(we.getKey()))
                    .count()
            ).sum();
        }).sum();
    }

    static double cost(Assignment a) {
        return offPreferenceCost(a);
    }

    @Test
    void print_threeBestRosters() {
        var improving = SOLVER.getSolutions(ROSTER, ParkrunSchedulingTest::cost).toList();
        var top3 = improving.subList(Math.max(0, improving.size() - 3), improving.size());

        System.out.printf("=== Three best rosters ===%n%n");
        for (int i = 0; i < top3.size(); i++) {
            System.out.printf("Roster %d%n", i + 1);
            printRoster(top3.get(i));
        }
    }

    private static void printRoster(Assignment a) {
        Map<Integer, Person> rdByWeek = new TreeMap<>();
        Map<Integer, Person> vcByWeek = new TreeMap<>();
        Z.forEach((p, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.forEach((w, v) -> {
                if (isTrue(a, v)) (r == Role.RD ? rdByWeek : vcByWeek).put(w, p);
            })));

        System.out.printf("  %-4s  %-16s  %s%n", "Week", "Run Director", "Volunteer Coordinator");
        for (int w = 1; w <= WEEKS; w++) {
            Person rd = rdByWeek.get(w);
            Person vc = vcByWeek.get(w);
            System.out.printf("  %-4d  %-16s  %s%n", w,
                rd == null ? "?" : rd.name() + (rd.prefers(w) ? "" : " *"),
                vc == null ? "?" : vc.name() + (vc.prefers(w) ? "" : " *"));
        }

        var counts = PEOPLE.stream().collect(Collectors.toMap(p -> p, p -> 0L));
        Z.forEach((p, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.values().forEach(v -> { if (isTrue(a, v)) counts.merge(p, 1L, Long::sum); })));
        System.out.printf("  Off-preference count: %d%n", (int) offPreferenceCost(a));
        counts.entrySet().stream()
            .sorted(Map.Entry.<Person, Long>comparingByValue().reversed())
            .forEach(e -> System.out.printf("    %-8s %d%n", e.getKey().name(), e.getValue()));
        System.out.printf("  Statistics: %s%n", a.getStatistics());
        System.out.println();
    }

    @Test
    void optimize_minimizesOffPreferenceAssignments() {
        val result = SOLVER.getSolution(ROSTER, ParkrunSchedulingTest::cost);
        assertThat(result).isPresent();
    }

    @Test
    void getSolutions_returnsImprovingRosters() {
        val improving = SOLVER.getSolutions(ROSTER, ParkrunSchedulingTest::cost).toList();
        assertThat(improving).isNotEmpty();
        for (int i = 1; i < improving.size(); i++) {
            assertThat(cost(improving.get(i))).isLessThan(cost(improving.get(i - 1)));
        }
    }
}
