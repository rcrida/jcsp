package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Finds a solution for {@link BranchAndBoundSolver} to start from, before its own search begins.
 *
 * <p>Branch-and-bound's weakness is the stretch before it has any incumbent: with no bound, nothing
 * prunes, and on a heavy-tailed problem one descent can commit to a subtree it never escapes. An
 * implementation of this is free to go about finding that first solution however it likes -- a
 * different search, a local search, a sequence of increasingly tight questions -- because none of it
 * can affect correctness. Only the incumbent crosses the boundary, and {@link BranchAndBoundSolver}
 * validates what it is handed before adopting it.
 *
 * <p>That containment is deliberate: every solution after the first still comes from
 * branch-and-bound's own search, so the bound, the objective cut and the completeness of the final
 * sweep -- and therefore the optimality proof a drained stream represents -- are untouched by
 * whatever happens in here. See
 * <a href="../../../../../../../docs/adr/0041-reuse-the-satisfaction-search-for-the-first-solution.md">ADR-0041</a>
 * and <a href="../../../../../../../docs/adr/0044-bounded-probes-for-the-starting-incumbent.md">ADR-0044</a>.
 */
public interface IncumbentSeeder {
    /**
     * The best feasible solution of {@code csp} this seeder can find cheaply, or {@link
     * Optional#empty()} if it finds none. {@code objective} is for ranking what it finds; an
     * implementation may ignore it and return any feasible solution.
     *
     * <p>Must not throw when a search it runs internally is cancelled or runs out of budget:
     * branch-and-bound truncates silently, and seeding is not the place to change that. An
     * implementation that finds nothing returns empty.
     */
    @NonNull Optional<Assignment> seed(@NonNull ConstraintSatisfactionProblem csp,
                                       @NonNull ToDoubleFunction<Assignment> objective);
}
