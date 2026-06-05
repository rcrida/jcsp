package io.github.rcrida.jcsp.solver.ignored;

import io.github.rcrida.jcsp.solver.LocalSolver;
import io.github.rcrida.jcsp.solver.assignmentfactory.FallbackAssignmentFactory;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cantor roster scheduling for two Sunday Masses (10am and 12pm), each requiring
 * a Cantor (voice) and an Organist: four roles per week.
 *
 * <p>Uses a binary-assignment formulation: one {@code Variable<Boolean>} per
 * (person, role, week) combination. Exactly-one constraints fill each slot;
 * at-most-one constraints prevent a person serving as both Cantor and Organist
 * at the same Mass; and cardinality constraints enforce a fair load.
 * The objective minimises the max-minus-min of cumulative total slots per person
 * (including carry-over from previous rosters).
 *
 * <p>Seven people: three voice-only (Alice, Bob, Carol), two organ-only (David, Eve),
 * and two dual-capability (Frank, Grace) who can sing or play at either Mass but not
 * fill both roles at the same Mass in the same week.
 */
public class ChoirRosterTest {
    enum Role { TEN_AM_CANTOR, TWELVE_PM_CANTOR, TEN_AM_ORGANIST, TWELVE_PM_ORGANIST }

    record Person(String name, Set<Role> roles, Set<Integer> unavailableWeeks, Set<Integer> assignedWeeks, int carryOver) {
        boolean canDo(Role role)      { return roles.contains(role); }
        boolean isAvailable(int week) { return !unavailableWeeks.contains(week); }
        boolean isAssigned(int week)  { return assignedWeeks.contains(week); }

        @Override public String toString() { return name; }
    }

    static final Set<Role> TEN_AM_CANTOR = Set.of(Role.TEN_AM_CANTOR);
    static final Set<Role> TWELVE_PM_CANTOR = Set.of(Role.TWELVE_PM_CANTOR);
    static final Set<Role> TEN_AM_ORGANIST = Set.of(Role.TEN_AM_ORGANIST);
    static final Set<Role> TWELVE_PM_ORGANIST = Set.of(Role.TWELVE_PM_ORGANIST);

    // Conflicting role pairs: cannot fill both roles at the same Mass in the same week
    static final List<Set<Role>> SAME_MASS_CONFLICTS = List.of(
        Set.of(Role.TEN_AM_CANTOR,    Role.TEN_AM_ORGANIST),
        Set.of(Role.TWELVE_PM_CANTOR, Role.TWELVE_PM_ORGANIST)
    );

    static final int WEEKS = 8;

    static final List<Person> PEOPLE = List.of(
            new Person("Fiona",    TEN_AM_CANTOR,      Set.of(1, 3, 6, 7, 8),       Set.of(), 0),
            new Person("Ed",       TEN_AM_CANTOR,      Set.of(1, 2, 3, 4, 6, 7, 8), Set.of(5), 0),
            new Person("Robert",   TEN_AM_CANTOR,      Set.of(1, 2, 6),             Set.of(), 0),
            new Person("Chris",    TEN_AM_CANTOR,      Set.of(1, 7),                Set.of(), 0),
            new Person("Philip",   TEN_AM_CANTOR,      Set.of(2, 3, 4, 6, 8),       Set.of(), 0),
            new Person("Michele",  TEN_AM_CANTOR,      Set.of(1, 6, 8),             Set.of(), 0),
            new Person("Sheldon",  TEN_AM_CANTOR,      Set.of(2, 6, 7, 8),          Set.of(), 0),
            new Person("Tessa",    TEN_AM_CANTOR,      Set.of(3),                   Set.of(), 0),
            new Person("Remilda",  TEN_AM_CANTOR,      Set.of(4, 7),                Set.of(), 0),
            new Person("Alice",    TEN_AM_CANTOR,      Set.of(1, 2, 3, 5, 6, 7, 8), Set.of(4), 0),
            new Person("Jillian",  TEN_AM_ORGANIST,    Set.of(),                    Set.of(), 0),
            new Person("Joseph",   TEN_AM_ORGANIST,    Set.of(2, 4, 6, 7, 8),       Set.of(), 0),
            new Person("Jeremy",   TEN_AM_ORGANIST,    Set.of(1, 2, 3, 5, 6, 7),    Set.of(4, 8), 0),
            new Person("Nanette",  TWELVE_PM_CANTOR,   Set.of(),                    Set.of(), 0),
            new Person("Phoebe",   TWELVE_PM_CANTOR,   Set.of(),                    Set.of(), 0),
            new Person("Pablo",    TWELVE_PM_CANTOR,   Set.of(1, 2, 3, 4, 5, 6, 7), Set.of(), 0),
            new Person("Ray",      TWELVE_PM_CANTOR,   Set.of(),                    Set.of(), 0),
            new Person("Dominic",  TWELVE_PM_CANTOR,   Set.of(),                    Set.of(), 0),
            new Person("Vivian",   TWELVE_PM_ORGANIST, Set.of(),                    Set.of(), 0)
    );

    static final Map<Role, Integer> ELIGIBLE_COUNT = Arrays.stream(Role.values())
        .collect(Collectors.toMap(r -> r, r -> (int) PEOPLE.stream().filter(c -> c.canDo(r)).count()));
    static final Map<Role, Integer> MIN_SLOTS = Arrays.stream(Role.values())
        .collect(Collectors.toMap(r -> r,
            r -> 0));
    static final Map<Role, Integer> MAX_SLOTS = Arrays.stream(Role.values())
        .collect(Collectors.toMap(r -> r,
            r -> (int) Math.ceil(2.0 * WEEKS / ELIGIBLE_COUNT.get(r))));

    /** z[person][role][week]: true iff that person fills that role that week. */
    static Map<Person, Map<Role, Map<Integer, Variable<Boolean>>>> Z;

    static final ConstraintSatisfactionProblem ROSTER = buildRoster();

    static ConstraintSatisfactionProblem buildRoster() {
        val csp = ConstraintSatisfactionProblem.builder();
        Z = new LinkedHashMap<>();

        for (Person c : PEOPLE) {
            Z.put(c, new LinkedHashMap<>());
            for (Role r : Role.values()) {
                if (!c.canDo(r)) continue;
                Z.get(c).put(r, new LinkedHashMap<>());
                for (int w = 1; w <= WEEKS; w++) {
                    if (!c.isAvailable(w)) continue;
                    Z.get(c).get(r).put(w, csp.createVariable(c + "_" + r + "_w" + w, BooleanDomain.INSTANCE));
                }
            }
        }

        // Each (role, week) slot must be filled by exactly one person
        for (Role r : Role.values()) {
            for (int w = 1; w <= WEEKS; w++) {
                csp.exactlyOneConstraint(slotVars(r, w));
            }
        }

        // A person cannot fill both Cantor and Organist at the same Mass in the same week
        for (Person c : PEOPLE) {
            for (Set<Role> conflict : SAME_MASS_CONFLICTS) {
                for (int w = 1; w <= WEEKS; w++) {
                    final int week = w;
                    var vars = conflict.stream()
                        .map(r -> Z.get(c).getOrDefault(r, Collections.emptyMap()).get(week))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                    if (vars.size() == 2) csp.atMostOneConstraint(vars);
                }
            }
        }

        // Each eligible person must serve within [min, max] times per role
        for (Person c : PEOPLE) {
            for (Role r : Role.values()) {
                if (!c.canDo(r)) continue;
                var vars = Set.copyOf(Z.get(c).get(r).values());
                csp.atLeastNConstraint(vars, MIN_SLOTS.get(r));
                csp.atMostNConstraint(vars, MAX_SLOTS.get(r));
            }
        }

        // Force pre-assigned slots: if a person is assigned to a week, their variable must be true
        for (Person c : PEOPLE) {
            for (Role r : Role.values()) {
                if (!c.canDo(r)) continue;
                for (int w = 1; w <= WEEKS; w++) {
                    if (!c.isAssigned(w)) continue;
                    var v = Z.get(c).get(r).get(w);
                    if (v != null) csp.equalsConstraint(v, true);
                }
            }
        }

        return csp.build();
    }

    static Set<Variable<Boolean>> slotVars(Role role, int week) {
        return PEOPLE.stream()
            .filter(c -> c.canDo(role))
            .map(c -> Z.get(c).get(role).get(week))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    static boolean isTrue(Assignment a, Variable<Boolean> v) {
        return a.getValue(v).orElse(false);
    }

    // Sum of (max-minus-min slot count) across each role pool.
    static double differenceCost(Assignment a) {
        return Arrays.stream(Role.values()).mapToDouble(r -> {
            var stats = PEOPLE.stream()
                .filter(c -> c.canDo(r))
                .mapToLong(c -> Z.get(c).getOrDefault(r, Map.of()).values().stream()
                    .filter(v -> isTrue(a, v)).count())
                .summaryStatistics();
            return stats.getCount() <= 1 ? 0 : stats.getMax() - stats.getMin();
        }).sum();
    }

    // Counts pairs of consecutive assigned weeks per person — lower is more spread out.
    static double consecutivePenalty(Assignment a) {
        double penalty = 0;
        for (Person c : PEOPLE) {
            var weeks = Z.get(c).values().stream()
                .flatMap(weekMap -> weekMap.entrySet().stream())
                .filter(e -> isTrue(a, e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
            for (int i = 0; i + 1 < weeks.size(); i++) {
                if (weeks.get(i + 1) - weeks.get(i) == 1) penalty++;
            }
        }
        return penalty;
    }

    static double cost(Assignment a) {
        return differenceCost(a) + consecutivePenalty(a);
    }

    private static void printRoster(Assignment a) {
        Map<Integer, Person> c10 = new TreeMap<>(), c12 = new TreeMap<>();
        Map<Integer, Person> o10 = new TreeMap<>(), o12 = new TreeMap<>();
        Z.forEach((c, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.forEach((w, v) -> {
                if (!isTrue(a, v)) return;
                switch (r) {
                    case TEN_AM_CANTOR     -> c10.put(w, c);
                    case TWELVE_PM_CANTOR  -> c12.put(w, c);
                    case TEN_AM_ORGANIST   -> o10.put(w, c);
                    case TWELVE_PM_ORGANIST -> o12.put(w, c);
                }
            })));

        System.out.printf("  %-4s  %-10s  %-10s  %-10s  %-10s%n",
            "Week", "10am Cantor", "12pm Cantor", "10am Organ", "12pm Organ");
        for (int w = 1; w <= WEEKS; w++) {
            System.out.printf("  %-4d  %-10s  %-10s  %-10s  %-10s%n", w,
                name(c10.get(w)), name(c12.get(w)), name(o10.get(w)), name(o12.get(w)));
        }

        var rosterCounts = PEOPLE.stream().collect(Collectors.toMap(c -> c, c -> 0L));
        Z.forEach((c, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.values().forEach(v -> { if (isTrue(a, v)) rosterCounts.merge(c, 1L, Long::sum); })));
        System.out.printf("  Role-diff-sum: %.0f  Consecutive-penalty: %.0f%n", differenceCost(a), consecutivePenalty(a));
        // nextCarry = surplus above the least-burdened person in the same role pool
        Map<Set<Role>, Long> minTotalByRole = new HashMap<>();
        for (Role r : Role.values()) {
            long min = PEOPLE.stream()
                .filter(c -> c.canDo(r))
                .mapToLong(c -> c.carryOver() + rosterCounts.get(c))
                .min().orElse(0);
            PEOPLE.stream().filter(c -> c.canDo(r)).forEach(c ->
                minTotalByRole.merge(c.roles(), min, Math::min));
        }
        rosterCounts.entrySet().stream()
            .sorted(Map.Entry.<Person, Long>comparingByValue().reversed())
            .forEach(e -> {
                Person c = e.getKey();
                long total = c.carryOver() + e.getValue();
                long minForRole = minTotalByRole.getOrDefault(c.roles(), 0L);
                System.out.printf("    %-10s this=%d  carry=%d  total=%d  nextCarry=%d%n",
                    c.name(), e.getValue(), c.carryOver(), total, total - minForRole);
            });
        System.out.printf("  Statistics: %s%n", a.getStatistics());
        System.out.println();
    }

    private static String name(Person c) { return c == null ? "?" : c.name(); }

    @Test
    void getLocalSolution() {
        val factory = FallbackAssignmentFactory.builder()
                .primary(ChoirRosterTest::initialAssignment).primaryCount(1)
                .fallback(RandomAssignmentFactory.INSTANCE).build();
        val solver = LocalSolver.Factory.INSTANCE.createLocalSolver(5, 20000, factory);
        val solution = solver.getLocalSolution(ROSTER, ChoirRosterTest::cost);
        assertThat(solution).isPresent();
        printRoster(solution.get());
    }

    // Slot-centric greedy initialisation: fill every (role, week) slot with exactly one person.
    // Load is tracked per role (not globally) so that dual-capability people are distributed
    // evenly within each role pool — a global counter would over-penalise them after cantor
    // roles are filled, starving them of organist slots and violating atLeastN.
    static Assignment initialAssignment(ConstraintSatisfactionProblem csp) {
        val builder = Assignment.builder();
        val cspVars = csp.getVariableDomains().keySet();

        // Only assign variables belonging to this (possibly sub-) CSP
        Z.forEach((c, roleMap) -> roleMap.forEach((r, weekMap) ->
            weekMap.forEach((w, v) -> { if (cspVars.contains(v)) builder.value(v, false); })));

        // Apply pre-assignments and track which (role, week) slots are already filled
        Set<String> preFilledSlots = new HashSet<>();
        for (Person c : PEOPLE) {
            for (Role r : Role.values()) {
                if (!c.canDo(r)) continue;
                for (int w = 1; w <= WEEKS; w++) {
                    if (!c.isAssigned(w)) continue;
                    var v = Z.get(c).get(r).get(w);
                    if (v != null && cspVars.contains(v)) {
                        builder.value(v, true);
                        preFilledSlots.add(r + "_" + w);
                    }
                }
            }
        }

        for (Role r : Role.values()) {
            Map<Person, Integer> roleLoad = new HashMap<>();
            PEOPLE.forEach(c -> roleLoad.put(c, (int) c.assignedWeeks().stream()
                    .filter(w -> c.canDo(r) && Z.get(c).getOrDefault(r, Map.of()).containsKey(w))
                    .count()));
            val weeks = new ArrayList<>(IntStream.rangeClosed(1, WEEKS).boxed().toList());
            Collections.shuffle(weeks, new Random(ThreadLocalRandom.current().nextLong()));
            for (int w : weeks) {
                if (preFilledSlots.contains(r + "_" + w)) continue;
                var candidates = PEOPLE.stream()
                    .filter(c -> c.canDo(r) && Z.get(c).get(r).containsKey(w)
                            && cspVars.contains(Z.get(c).get(r).get(w))
                            && roleLoad.get(c) < MAX_SLOTS.get(r))
                    .toList();
                if (candidates.isEmpty()) continue;
                var chosen = candidates.stream()
                    .min(Comparator.comparingInt(roleLoad::get))
                    .orElseThrow();
                builder.value(Z.get(chosen).get(r).get(w), true);
                roleLoad.merge(chosen, 1, Integer::sum);
            }
        }
        return builder.build();
    }
}
