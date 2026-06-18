package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates InverseConstraint for bidirectional task assignment.
 *
 * <p>Four engineers are each assigned exactly one of four tasks (a permutation).
 * Each engineer has a restricted skill set, modelled as their domain:
 * <ul>
 *   <li>Engineer 1: tasks {1, 2}</li>
 *   <li>Engineer 2: tasks {2, 3}</li>
 *   <li>Engineer 3: tasks {3, 4}</li>
 *   <li>Engineer 4: tasks {1, 4}</li>
 * </ul>
 *
 * <p>The {@code inverse} constraint links the forward mapping ({@code task[i]} = task for
 * engineer {@code i+1}) to the backward mapping ({@code lead[j-1]} = engineer leading task
 * {@code j}), enabling efficient lookup in both directions and bidirectional propagation.
 *
 * <p>Combined with AllDiff on {@code task}, exactly two valid assignments exist:
 * <pre>
 *   [1, 2, 3, 4] → engineers 1..4 lead tasks 1..4 respectively
 *   [2, 3, 4, 1] → engineer 4 leads task 1; 1 leads task 2; 2 leads task 3; 3 leads task 4
 * </pre>
 */
public class TaskAssignmentInverseTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 4;

    // task[i] = task number (1..N) assigned to engineer i+1
    static final Variable<Integer> task1 = F.create("task1");
    static final Variable<Integer> task2 = F.create("task2");
    static final Variable<Integer> task3 = F.create("task3");
    static final Variable<Integer> task4 = F.create("task4");

    // lead[j] = engineer number (1..N) leading task j+1
    static final Variable<Integer> lead1 = F.create("lead1");
    static final Variable<Integer> lead2 = F.create("lead2");
    static final Variable<Integer> lead3 = F.create("lead3");
    static final Variable<Integer> lead4 = F.create("lead4");

    static final List<Variable<Integer>> tasks = List.of(task1, task2, task3, task4);
    static final List<Variable<Integer>> leads = List.of(lead1, lead2, lead3, lead4);

    static ConstraintSatisfactionProblem buildProblem() {
        return ConstraintSatisfactionProblem.builder()
                // Skill domains: each engineer can only do certain tasks
                .variableDomain(task1, DomainObjectSet.<Integer>builder().value(1).value(2).build())
                .variableDomain(task2, DomainObjectSet.<Integer>builder().value(2).value(3).build())
                .variableDomain(task3, DomainObjectSet.<Integer>builder().value(3).value(4).build())
                .variableDomain(task4, DomainObjectSet.<Integer>builder().value(1).value(4).build())
                // Inverse mapping: knowing which engineer leads each task
                .variableDomain(lead1, IntRangeDomain.of(1, N))
                .variableDomain(lead2, IntRangeDomain.of(1, N))
                .variableDomain(lead3, IntRangeDomain.of(1, N))
                .variableDomain(lead4, IntRangeDomain.of(1, N))
                // Each engineer gets a different task (permutation)
                .allDiffConstraint(Set.of(task1, task2, task3, task4))
                // Bidirectional consistency: task[i]==j ↔ lead[j-1]==i+1
                .inverseConstraint(tasks, leads)
                .build();
    }

    @Test
    void exactlyTwoSolutions() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildProblem()).getSolutions().toList();
        assertThat(solutions).hasSize(2);
    }

    @Test
    void solutionsAreValid() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildProblem()).getSolutions().toList();

        assertThat(solutions).allSatisfy(sol -> {
            // Forward: task[i] gives the task for engineer i+1
            // Backward: lead[j-1] gives the engineer for task j
            // Verify bidirectional consistency
            for (int i = 0; i < N; i++) {
                int taskNum = sol.getValue(tasks.get(i)).orElseThrow(); // 1-based task
                int engineerNum = sol.getValue(leads.get(taskNum - 1)).orElseThrow(); // 1-based engineer
                assertThat(engineerNum).isEqualTo(i + 1);
            }
        });
    }

    @Test
    void firstSolution_engineersFollowSkillDomains() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildProblem()).getSolutions().toList();

        assertThat(solutions).allSatisfy(sol -> {
            // Each engineer's assigned task must be in their skill domain
            assertThat(sol.getValue(task1).orElseThrow()).isIn(1, 2);
            assertThat(sol.getValue(task2).orElseThrow()).isIn(2, 3);
            assertThat(sol.getValue(task3).orElseThrow()).isIn(3, 4);
            assertThat(sol.getValue(task4).orElseThrow()).isIn(1, 4);
        });
    }

    @Test
    void knownAssignments() {
        val solutions = Solver.Factory.INSTANCE.createSolver(buildProblem()).getSolutions().toList();

        // Solution A: [1,2,3,4] — engineer i+1 leads task i+1, inverse is identity
        assertThat(solutions).anySatisfy(sol -> {
            assertThat(sol.getValue(task1)).hasValue(1);
            assertThat(sol.getValue(task2)).hasValue(2);
            assertThat(sol.getValue(task3)).hasValue(3);
            assertThat(sol.getValue(task4)).hasValue(4);
            assertThat(sol.getValue(lead1)).hasValue(1);
            assertThat(sol.getValue(lead2)).hasValue(2);
            assertThat(sol.getValue(lead3)).hasValue(3);
            assertThat(sol.getValue(lead4)).hasValue(4);
        });

        // Solution B: [2,3,4,1] — cyclic shift; engineer 4 leads task 1
        assertThat(solutions).anySatisfy(sol -> {
            assertThat(sol.getValue(task1)).hasValue(2);
            assertThat(sol.getValue(task2)).hasValue(3);
            assertThat(sol.getValue(task3)).hasValue(4);
            assertThat(sol.getValue(task4)).hasValue(1);
            assertThat(sol.getValue(lead1)).hasValue(4);
            assertThat(sol.getValue(lead2)).hasValue(1);
            assertThat(sol.getValue(lead3)).hasValue(2);
            assertThat(sol.getValue(lead4)).hasValue(3);
        });
    }
}
