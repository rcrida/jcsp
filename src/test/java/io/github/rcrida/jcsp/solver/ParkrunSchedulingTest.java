package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eight weeks of parkrun scheduling with two roles per week:
 * Run Director (RD) and Volunteer Coordinator (VC).
 *
 * <p>Uses a binary-assignment formulation: one {@code Variable<Boolean>} per
 * (person, role, week) combination. Exactly-one constraints per slot, at-most-one
 * constraints for dual-role people within a week, and cardinality constraints enforce
 * a fair load. The off-preference objective counts {@code true} assignments in
 * non-preferred weeks and is naturally admissible for B&B because unassigned variables
 * default to {@code false} (contributing 0).
 *
 * <p>Nine people cover 8 weeks — three RD-only (Sarah, Tom, Frank), three VC-only
 * (Carol, Dave, Grace), and three dual-role (Eve, Mark, Henry). Each eligible person
 * volunteers between {@code RD_MIN}/{@code VC_MIN} and {@code RD_MAX}/{@code VC_MAX}
 * times per role, computed from the average slots per eligible person.
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
        new Person("Eve",   Set.of(Role.RD, Role.VC),   ALL_WEEKS,                    Set.of(7, 8)),
        new Person("Mark",  Set.of(Role.RD, Role.VC),   Set.of(1,2,3,4,5,6,7),       Set.of(9, 12)),
        new Person("Henry", Set.of(Role.RD, Role.VC),   Set.of(5,6,7,8,9,10,11),     Set.of(3, 8, 9))
    );

    // Per-role slot bounds: average = WEEKS / eligible_count.
    // Each person volunteers at least ceil(average/2) and at most floor(average*2) times per role.
    static final int RD_ELIGIBLE_COUNT = (int) PEOPLE.stream().filter(p -> p.canDo(Role.RD)).count();
    static final int VC_ELIGIBLE_COUNT = (int) PEOPLE.stream().filter(p -> p.canDo(Role.VC)).count();
    static final int RD_MIN = (int) Math.ceil((double)  WEEKS / (2 * RD_ELIGIBLE_COUNT));
    static final int RD_MAX = (int) Math.floor(2.0 * WEEKS    / RD_ELIGIBLE_COUNT);
    static final int VC_MIN = (int) Math.ceil((double)  WEEKS / (2 * VC_ELIGIBLE_COUNT));
    static final int VC_MAX = (int) Math.floor(2.0 * WEEKS    / VC_ELIGIBLE_COUNT);

    // Precomputed minimum achievable max-minus-min of total slots per person across both roles.
    // Enumerated by trying all ways to distribute the per-role extra slots.
    static final double MINIMUM_DIFFERENCE = computeMinimumDifference();

    private static double computeMinimumDifference() {
        var rdEligible = PEOPLE.stream().filter(p -> p.canDo(Role.RD)).toList();
        var vcEligible = PEOPLE.stream().filter(p -> p.canDo(Role.VC)).toList();
        int rdBase = WEEKS / rdEligible.size(), rdExtras = WEEKS % rdEligible.size();
        int vcBase = WEEKS / vcEligible.size(), vcExtras = WEEKS % vcEligible.size();
        double minDiff = Double.MAX_VALUE;
        for (int rdMask = 0; rdMask < (1 << rdEligible.size()); rdMask++) {
            if (Integer.bitCount(rdMask) != rdExtras) continue;
            for (int vcMask = 0; vcMask < (1 << vcEligible.size()); vcMask++) {
                if (Integer.bitCount(vcMask) != vcExtras) continue;
                final int rm = rdMask, vm = vcMask;
                double[] totals = IntStream.range(0, PEOPLE.size()).mapToDouble(i -> {
                    Person p = PEOPLE.get(i);
                    int rdIdx = rdEligible.indexOf(p);
                    int vcIdx = vcEligible.indexOf(p);
                    return (rdIdx >= 0 ? rdBase + ((rm >> rdIdx) & 1) : 0)
                         + (vcIdx >= 0 ? vcBase + ((vm >> vcIdx) & 1) : 0);
                }).toArray();
                double diff = Arrays.stream(totals).max().orElse(0) - Arrays.stream(totals).min().orElse(0);
                minDiff = Math.min(minDiff, diff);
            }
        }
        return minDiff;
    }

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
                csp.exactlyOneConstraint(slotVars(r, w));
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
                var personRoleVars = Set.copyOf(Z.get(p).get(r).values());
                int min = r == Role.RD ? RD_MIN : VC_MIN;
                csp.atLeastNConstraint(personRoleVars, min);
                int max = r == Role.RD ? RD_MAX : VC_MAX;
                csp.atMostNConstraint(personRoleVars, max);
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
        return a.getValue(v).orElse(false);
    }

    // Tight lower bound on off-preference cost.
    // For each slot, contributes the minimum possible off-preference:
    //   - already filled by a preferred person → 0
    //   - already filled by a non-preferred person → 1
    //   - unfilled, but at least one preferred candidate's variable is still unassigned → 0 (optimistic)
    //   - unfilled, no preferred candidate can still be chosen → 1 (unavoidable)
    static double offPreferenceCost(Assignment a) {
        double cost = 0;
        for (Role r : Role.values()) {
            for (int w = 1; w <= WEEKS; w++) {
                final int week = w;
                cost += slotCost(a, r, week);
            }
        }
        return cost;
    }

    private static double slotCost(Assignment a, Role role, int week) {
        for (Person p : PEOPLE) {
            if (!p.canDo(role)) continue;
            var v = Z.get(p).get(role).get(week);
            if (v != null && isTrue(a, v)) return p.prefers(week) ? 0 : 1; // slot filled
        }
        // Slot unfilled: 0 if any preferred candidate's variable is still unassigned, else 1
        boolean anyPreferredPossible = PEOPLE.stream()
            .filter(p -> p.canDo(role) && p.prefers(week))
            .anyMatch(p -> {
                var v = Z.get(p).get(role).get(week);
                return v != null && a.getValue(v).isEmpty();
            });
        return anyPreferredPossible ? 0 : 1;
    }

    // Max-minus-min of total slots (RD + VC) per person across all people.
    static double differenceCost(Assignment a) {
        double[] totals = personTotals(a);
        return Arrays.stream(totals).max().orElse(0) - Arrays.stream(totals).min().orElse(0);
    }

    private static double[] personTotals(Assignment a) {
        return PEOPLE.stream().mapToDouble(p ->
            Z.get(p).values().stream()
                .flatMap(weekMap -> weekMap.values().stream())
                .filter(v -> isTrue(a, v))
                .count()
        ).toArray();
    }

    static double cost(Assignment a) {
        if (a.isComplete(ROSTER)) return offPreferenceCost(a) + differenceCost(a);
        return offPreferenceCost(a) + MINIMUM_DIFFERENCE;
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
        System.out.printf("  Off-preference: %d  Max-diff: %.0f%n",
            (int) offPreferenceCost(a), differenceCost(a));
        counts.entrySet().stream()
            .sorted(Map.Entry.<Person, Long>comparingByValue().reversed())
            .forEach(e -> System.out.printf("    %-8s %d%n", e.getKey().name(), e.getValue()));
        System.out.printf("  Statistics: %s%n", a.getStatistics());
        System.out.println();
    }

    @Test
    void getLocalSolution() {
        val solver = MinConflictsSolver.of(4000);
        Optional<Assignment>  solution = Optional.empty();
        for (int attempt = 0; attempt < 20 && solution.isEmpty(); attempt++) {
            solution = solver.getLocalSolution(ROSTER, ParkrunSchedulingTest::initialAssignment, ParkrunSchedulingTest::cost);
        }
        assertThat(solution).isPresent();
        printRoster(solution.get());
    }

    // Slot-centric greedy initialisation: fill every (role, week) slot with exactly one person,
    // satisfying exactlyOneConstraint from the start. Among eligible candidates for each slot,
    // prefer those who want that week; break ties by least total load so far. This produces
    // far fewer constraint violations than the person-centric random approach, giving
    // MinConflictsSolver a much better starting point.
    static Assignment initialAssignment(ConstraintSatisfactionProblem csp) {
        val builder = Assignment.builder();
        // Default all variables to false
        Z.forEach((p, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.values().forEach(v -> builder.value(v, false))));
        // Track running load per person to balance assignments
        Map<Person, Integer> load = new HashMap<>();
        PEOPLE.forEach(p -> load.put(p, 0));
        for (Role r : Role.values()) {
            // Shuffle week order to avoid systematic bias
            val weeks = new ArrayList<>(IntStream.rangeClosed(1, WEEKS).boxed().toList());
            Collections.shuffle(weeks, new Random(ThreadLocalRandom.current().nextLong()));
            for (int w : weeks) {
                var candidates = PEOPLE.stream()
                    .filter(p -> p.canDo(r) && Z.get(p).get(r).containsKey(w))
                    .toList();
                if (candidates.isEmpty()) continue;
                // Prefer preferred+least-loaded; fall back to least-loaded
                var chosen = candidates.stream()
                    .filter(p -> p.prefers(w))
                    .min(Comparator.comparingInt(load::get))
                    .or(() -> candidates.stream().min(Comparator.comparingInt(load::get)))
                    .orElseThrow();
                builder.value(Z.get(chosen).get(r).get(w), true);
                load.merge(chosen, 1, Integer::sum);
            }
        }
        return builder.build();
    }
}
