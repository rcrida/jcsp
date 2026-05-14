package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timetabling as a multi-dimensional assignment problem with boolean (true/false) variables,
 * using the same teachers, subjects and groups as {@link TimetableSchedulingViaColouringTest}.
 *
 * <p>Each variable z[lesson][teacherSlot] represents "is lesson L taught by teacher T in slot S?".
 * This formulation models teacher assignment, room specialisation, and teacher unavailability
 * explicitly. Unavailability is encoded directly in each lesson's variable domain via
 * {@link #wholeDay}, {@link #everyDay}, and {@link #periodsOn} — no additional constraints needed.
 *
 * <p>Nine lessons (3 groups × 3 subjects) across 3 days × 2 periods and 4 rooms
 * (2 classrooms, 1 lab, 1 computer lab).
 */
public class TimetableSchedulingBinaryAssignmentTest {

    enum Day     { MON, TUE, WED }
    enum Period  { MORNING, AFTERNOON }
    enum RoomType { CLASSROOM, LAB, COMPUTER_LAB }
    enum Room {
        ROOM_A(RoomType.CLASSROOM),
        ROOM_B(RoomType.CLASSROOM),
        ROOM_C(RoomType.LAB),
        ROOM_D(RoomType.COMPUTER_LAB);
        final RoomType type;
        Room(RoomType type) { this.type = type; }
    }
    enum Teacher { DR_SMITH, DR_JONES, DR_BROWN }
    enum Subject { MATH, PHYSICS, CHEMISTRY, BIOLOGY, ENGLISH, HISTORY, COMPUTER_SCIENCE }
    enum Group   { SCIENCE, TECHNOLOGY, HUMANITIES }

    record Timeslot(Day day, Period period) {
        @Override public String toString() { return day + "_" + period; }
    }

    record Slot(Timeslot timeslot, Room room) {
        @Override public String toString() { return timeslot + "@" + room; }
    }

    record TeacherSlot(Teacher teacher, Slot slot) {
        @Override public String toString() { return teacher + "@" + slot; }
    }

    record Lesson(Group group, Subject subject) {
        @Override public String toString() { return group + "/" + subject; }
    }

    // --- Unavailability helpers ---

    /** Teacher unavailable for all periods on a given day. */
    static Set<Timeslot> wholeDay(Day day) {
        return Arrays.stream(Period.values()).map(p -> new Timeslot(day, p)).collect(Collectors.toSet());
    }

    /** Teacher unavailable during a given period on every day. */
    static Set<Timeslot> everyDay(Period period) {
        return Arrays.stream(Day.values()).map(d -> new Timeslot(d, period)).collect(Collectors.toSet());
    }

    /** Teacher unavailable during specific periods on a given day. */
    static Set<Timeslot> periodsOn(Day day, Period... periods) {
        return Arrays.stream(periods).map(p -> new Timeslot(day, p)).collect(Collectors.toSet());
    }

    // --- Problem data ---

    static final Map<Teacher, Set<Timeslot>> UNAVAILABLE = Map.of(
            Teacher.DR_SMITH, wholeDay(Day.MON),                          // departmental meeting
            Teacher.DR_JONES, everyDay(Period.AFTERNOON),                  // teaches at another school in afternoons
            Teacher.DR_BROWN, periodsOn(Day.WED, Period.MORNING) // unavailable Wednesday morning
    );

    static final Map<Subject, RoomType> REQUIRED_ROOM_TYPE = Map.of(
            Subject.MATH,             RoomType.CLASSROOM,
            Subject.PHYSICS,          RoomType.LAB,
            Subject.CHEMISTRY,        RoomType.LAB,
            Subject.BIOLOGY,          RoomType.LAB,
            Subject.ENGLISH,          RoomType.CLASSROOM,
            Subject.HISTORY,          RoomType.CLASSROOM,
            Subject.COMPUTER_SCIENCE, RoomType.COMPUTER_LAB
    );

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

    static final List<Timeslot> TIMESLOTS = Arrays.stream(Day.values())
            .flatMap(d -> Arrays.stream(Period.values()).map(p -> new Timeslot(d, p)))
            .toList();

    static final List<Lesson> LESSONS = CURRICULUM.entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(s -> new Lesson(e.getKey(), s)))
            .toList();

    static final List<Slot> SLOTS = TIMESLOTS.stream()
            .flatMap(t -> Arrays.stream(Room.values()).map(r -> new Slot(t, r)))
            .toList();

    /** z[lesson][teacherSlot]: true if lesson is taught by teacher in slot. Set by {@link #timetabling()}. */
    static Map<Lesson, Map<TeacherSlot, Variable>> Z;

    static List<TeacherSlot> eligibleTeacherSlots(Lesson lesson) {
        val requiredType = REQUIRED_ROOM_TYPE.get(lesson.subject());
        return ELIGIBLE_TEACHERS.get(lesson.subject()).stream()
                .flatMap(t -> TIMESLOTS.stream()
                        .filter(ts -> !UNAVAILABLE.getOrDefault(t, Set.of()).contains(ts))
                        .flatMap(ts -> Arrays.stream(Room.values())
                                .filter(r -> r.type == requiredType)
                                .map(r -> new TeacherSlot(t, new Slot(ts, r)))))
                .toList();
    }

    static ConstraintSatisfactionProblem timetabling() {
        val csp = ConstraintSatisfactionProblem.builder();

        Z = new LinkedHashMap<>();
        for (Lesson lesson : LESSONS) {
            val tsVars = new LinkedHashMap<TeacherSlot, Variable>();
            for (TeacherSlot ts : eligibleTeacherSlots(lesson)) {
                tsVars.put(ts, csp.createVariable(lesson + "@" + ts, BooleanDomain.INSTANCE));
            }
            Z.put(lesson, tsVars);
        }

        // Each lesson must be assigned to exactly one (teacher, slot) (n-ary: sum == 1)
        for (Lesson lesson : LESSONS) {
            val lessonVars = Set.copyOf(Z.get(lesson).values());
            csp.predicateConstraint(lessonVars, assignment ->
                    lessonVars.stream().mapToInt(v -> (Boolean) assignment.getValue(v).orElse(false) ? 1 : 0).sum() == 1);
        }

        // At most one (teacher, slot) per lesson (binary decomposition for min-conflicts guidance)
        for (Lesson lesson : LESSONS) {
            csp.atMostOneConstraint(Set.copyOf(Z.get(lesson).values()));
        }

        // No two lessons can share the same room at the same time
        for (Slot slot : SLOTS) {
            csp.atMostOneConstraint(LESSONS.stream()
                    .flatMap(l -> Z.get(l).entrySet().stream()
                            .filter(e -> e.getKey().slot().equals(slot))
                            .map(Map.Entry::getValue))
                    .collect(Collectors.toSet()));
        }

        // No teacher can teach two lessons at the same time
        for (Teacher teacher : Teacher.values()) {
            for (Timeslot timeslot : TIMESLOTS) {
                val vars = LESSONS.stream()
                        .flatMap(l -> Z.get(l).entrySet().stream()
                                .filter(e -> e.getKey().teacher() == teacher
                                        && e.getKey().slot().timeslot().equals(timeslot))
                                .map(Map.Entry::getValue))
                        .collect(Collectors.toSet());
                if (vars.size() > 1) csp.atMostOneConstraint(vars);
            }
        }

        // Within-group: no student group can attend two lessons at the same time
        for (Group group : Group.values()) {
            for (Timeslot timeslot : TIMESLOTS) {
                csp.atMostOneConstraint(LESSONS.stream()
                        .filter(l -> l.group() == group)
                        .flatMap(l -> Z.get(l).entrySet().stream()
                                .filter(e -> e.getKey().slot().timeslot().equals(timeslot))
                                .map(Map.Entry::getValue))
                        .collect(Collectors.toSet()));
            }
        }

        return csp.build();
    }

    /**
     * Initial assignment: exactly one randomly chosen (teacher, slot) per lesson set to true, all others false.
     * Satisfies the "exactly one per lesson" constraint from the start so min-conflicts only
     * needs to resolve teacher, group, and room conflicts.
     */
    static Assignment initialAssignment(ConstraintSatisfactionProblem csp) {
        val builder = Assignment.builder();
        for (Lesson lesson : LESSONS) {
            val teacherSlots = new ArrayList<>(Z.get(lesson).keySet());
            int chosen = ThreadLocalRandom.current().nextInt(teacherSlots.size());
            for (int i = 0; i < teacherSlots.size(); i++) {
                builder.value(Z.get(lesson).get(teacherSlots.get(i)), i == chosen);
            }
        }
        return builder.build();
    }

    static void printTimetable(Assignment solution) {
        val lookup = new HashMap<Timeslot, Map<Group, String>>();
        for (Timeslot ts : TIMESLOTS) lookup.put(ts, new HashMap<>());
        for (Lesson lesson : LESSONS) {
            Z.get(lesson).entrySet().stream()
                    .filter(e -> solution.getValue(e.getValue()).equals(Optional.of(true)))
                    .findFirst()
                    .ifPresent(e -> lookup.get(e.getKey().slot().timeslot()).put(lesson.group(),
                            lesson.subject() + " (" + e.getKey().teacher() + ", " + e.getKey().slot().room() + ")"));
        }

        int col = 36;
        String sep = "-".repeat(14 + (col + 2) * Group.values().length + 1);
        System.out.println("\n--- Timetable ---");
        System.out.printf("%-14s", "");
        for (Group g : Group.values()) System.out.printf("| %-" + col + "s", g);
        System.out.println("|");
        System.out.println(sep);
        for (Timeslot ts : TIMESLOTS) {
            System.out.printf("%-14s", ts);
            for (Group g : Group.values()) System.out.printf("| %-" + col + "s", lookup.get(ts).getOrDefault(g, ""));
            System.out.println("|");
        }
    }

    @Test
    void localSolution() {
        val problem = timetabling();
        // The binary formulation requires two variable changes per lesson move (old teacherSlot → false,
        // new teacherSlot → true), so the solver can cycle in the invalid intermediate state where a lesson
        // has no assignment. Restarting with a new random initial assignment escapes these cycles.
        val solver = new MinConflictsSolver(2000);
        Optional<Assignment> solution = Optional.empty();
        for (int attempt = 0; attempt < 20 && solution.isEmpty(); attempt++) {
            solution = solver.getLocalSolution(problem, TimetableSchedulingBinaryAssignmentTest::initialAssignment);
        }
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(problem)).isTrue();
        printTimetable(solution.get());
    }
}
