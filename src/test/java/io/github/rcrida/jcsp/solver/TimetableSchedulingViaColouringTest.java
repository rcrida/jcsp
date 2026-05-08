package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timetable scheduling modelled as graph colouring. Each (Group, Subject) pairing is a
 * variable whose domain is the set of valid (TimeSlot, Teacher) assignments for that
 * subject. This models multiple sections of the same subject taught to different student
 * groups, potentially by different teachers at different times.
 *
 * <pre>
 * Eligible teachers per subject:
 *   MATH             → DR_SMITH
 *   PHYSICS          → DR_SMITH, DR_JONES   (Dr. Jones also qualified)
 *   CHEMISTRY        → DR_JONES
 *   BIOLOGY          → DR_JONES
 *   ENGLISH          → DR_BROWN
 *   HISTORY          → DR_BROWN
 *   COMPUTER_SCIENCE → DR_SMITH
 *
 * Curriculum (subjects per group — overlapping on MATH and CHEMISTRY):
 *   SCIENCE     → MATH, CHEMISTRY, BIOLOGY
 *   TECHNOLOGY  → COMPUTER_SCIENCE, PHYSICS, MATH
 *   HUMANITIES  → ENGLISH, HISTORY, CHEMISTRY
 *
 * Constraints:
 *   Within-group:   all subjects in the same group must be in different slots
 *                   (students cannot attend two classes at once)
 *   Cross-group:    variables from different groups whose eligible teacher sets
 *                   overlap get a NO_TEACHER_CLASH constraint
 *                   (a teacher cannot teach two classes simultaneously)
 * </pre>
 */
public class TimetableSchedulingViaColouringTest {
    enum TimeSlot { SLOT_1, SLOT_2, SLOT_3, SLOT_4 }
    enum Teacher  { DR_SMITH, DR_JONES, DR_BROWN }
    enum Subject  { MATH, PHYSICS, CHEMISTRY, BIOLOGY, ENGLISH, HISTORY, COMPUTER_SCIENCE }
    enum Group    { SCIENCE, TECHNOLOGY, HUMANITIES }

    record ClassSchedule(TimeSlot slot, Teacher teacher) {
        @Override public String toString() { return slot + "/" + teacher; }
    }

    static final Map<Subject, Set<Teacher>> ELIGIBLE_TEACHERS = Map.of(
            Subject.MATH,             Set.of(Teacher.DR_SMITH),
            Subject.PHYSICS,          Set.of(Teacher.DR_SMITH, Teacher.DR_JONES),
            Subject.CHEMISTRY,        Set.of(Teacher.DR_JONES),
            Subject.BIOLOGY,          Set.of(Teacher.DR_JONES),
            Subject.ENGLISH,          Set.of(Teacher.DR_BROWN),
            Subject.HISTORY,          Set.of(Teacher.DR_BROWN),
            Subject.COMPUTER_SCIENCE, Set.of(Teacher.DR_SMITH)
    );

    static final Map<Group, List<Subject>> CURRICULUM = new EnumMap<>(Map.of(
            Group.SCIENCE,    List.of(Subject.MATH, Subject.CHEMISTRY, Subject.BIOLOGY),
            Group.TECHNOLOGY, List.of(Subject.COMPUTER_SCIENCE, Subject.PHYSICS, Subject.MATH),
            Group.HUMANITIES, List.of(Subject.ENGLISH, Subject.HISTORY, Subject.CHEMISTRY)
    ));

    static final Map<Group, Map<Subject, Variable>> VARIABLES = buildVariables();

    static Map<Group, Map<Subject, Variable>> buildVariables() {
        val factory = Variable.Factory.INSTANCE;
        val result = new EnumMap<Group, Map<Subject, Variable>>(Group.class);
        CURRICULUM.forEach((group, subjects) -> {
            val subjectVars = new EnumMap<Subject, Variable>(Subject.class);
            subjects.forEach(subject -> subjectVars.put(subject, factory.create(group + "_" + subject)));
            result.put(group, subjectVars);
        });
        return Collections.unmodifiableMap(result);
    }

    // Courses in the same student group must occupy different time slots.
    static final BiPredicate<Object, Object> DIFFERENT_SLOT =
            (a, b) -> ((ClassSchedule) a).slot() != ((ClassSchedule) b).slot();

    // A teacher cannot teach two courses simultaneously.
    static final BiPredicate<Object, Object> NO_TEACHER_CLASH =
            (a, b) -> {
                val as = (ClassSchedule) a;
                val bs = (ClassSchedule) b;
                return as.slot() != bs.slot() || as.teacher() != bs.teacher();
            };

    static DomainObjectSet domainFor(Subject subject) {
        val builder = DomainObjectSet.builder();
        for (TimeSlot slot : TimeSlot.values())
            for (Teacher teacher : ELIGIBLE_TEACHERS.get(subject))
                builder.value(new ClassSchedule(slot, teacher));
        return builder.build();
    }

    static ConstraintSatisfactionProblem timetable() {
        val builder = ConstraintSatisfactionProblem.builder();

        // Register each (group, subject) variable with its domain
        CURRICULUM.forEach((group, subjects) ->
                subjects.forEach(subject ->
                        builder.variableDomain(VARIABLES.get(group).get(subject), domainFor(subject))));

        // Within-group: all subjects in the same group must be in different slots
        CURRICULUM.forEach((group, subjects) -> {
            for (int i = 0; i < subjects.size(); i++)
                for (int j = i + 1; j < subjects.size(); j++)
                    builder.biPredicateConstraint(
                            VARIABLES.get(group).get(subjects.get(i)),
                            VARIABLES.get(group).get(subjects.get(j)),
                            DIFFERENT_SLOT);
        });

        // Cross-group: teacher clash between variables from different groups
        // whose eligible teacher sets overlap
        val pairings = new ArrayList<Map.Entry<Group, Subject>>();
        CURRICULUM.forEach((group, subjects) ->
                subjects.forEach(subject -> pairings.add(Map.entry(group, subject))));

        for (int i = 0; i < pairings.size(); i++) {
            for (int j = i + 1; j < pairings.size(); j++) {
                val p1 = pairings.get(i);
                val p2 = pairings.get(j);
                if (p1.getKey() != p2.getKey()) {
                    val teachers1 = ELIGIBLE_TEACHERS.get(p1.getValue());
                    val teachers2 = ELIGIBLE_TEACHERS.get(p2.getValue());
                    if (!Collections.disjoint(teachers1, teachers2))
                        builder.biPredicateConstraint(
                                VARIABLES.get(p1.getKey()).get(p1.getValue()),
                                VARIABLES.get(p2.getKey()).get(p2.getValue()),
                                NO_TEACHER_CLASH);
                }
            }
        }

        return builder.build();
    }

    @Test
    void findSchedule() {
        val csp = timetable();
        val solution = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        assertThat(solution).hasValueSatisfying(assignment -> {
            assertThat(assignment.isSolution(csp)).isTrue();
            System.out.println("Schedule:   " + assignment);
            System.out.println("Statistics: " + assignment.getStatistics());
        });
    }

    @Test
    void allSchedules() {
        val csp = timetable();
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp).toList();
        System.out.println("Total valid schedules: " + solutions.size());
        assertThat(solutions).hasSize(2880);
    }
}
