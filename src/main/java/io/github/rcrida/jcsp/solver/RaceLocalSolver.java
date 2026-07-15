package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Races multiple {@link LocalSolver} strategies against the same problem and returns whichever
 * produces a result first, rather than committing to a single strategy up front — this sidesteps
 * needing a heuristic to predict which strategy suits a given problem shape (a router built on
 * such a heuristic was tried and falsified before for a different pair of solvers; see
 * {@code BacktrackingSearch} vs {@code DomWdegLubySearch} in project history).
 * <p>
 * Delegates implementing the package-private {@link CancellableLocalSolver} contract (currently
 * {@link MinConflictsSolver} and {@link TabuSearchSolver}) are handed a shared {@link Cancellation}
 * token that's tripped as soon as any delegate wins, so the losers stop at their next search step
 * instead of running to their own completion for no benefit. Other delegates just run to
 * completion in the background once they've lost the race.
 * <p>
 * A delegate returning {@link Optional#empty()} doesn't end the race — the other delegates keep
 * running, and {@link Optional#empty()} is only returned once every delegate has finished without
 * a result.
 */
@Slf4j
@Value
@Builder
public class RaceLocalSolver implements LocalSolver {
    @Singular List<LocalSolver> delegates;

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return race(delegate -> delegate.getLocalSolution(csp));
    }

    @Override
    public Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp,
                                                 @NonNull ToDoubleFunction<Assignment> objective) {
        return race(delegate -> delegate.getLocalSolution(csp, objective));
    }

    private Optional<Assignment> race(@NonNull Function<LocalSolver, Optional<Assignment>> solve) {
        assert !delegates.isEmpty() : "RaceLocalSolver requires at least one delegate";

        var cancellation = new Cancellation();
        var promise = new CompletableFuture<Optional<Assignment>>();
        var pending = new AtomicInteger(delegates.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var delegate : delegates) {
                var effective = delegate instanceof CancellableLocalSolver cancellable
                        ? cancellable.withCancellation(cancellation)
                        : delegate;
                CompletableFuture.supplyAsync(() -> solve.apply(effective), executor)
                        .whenComplete((result, error) -> {
                            boolean isLast = pending.decrementAndGet() == 0;
                            if (error != null) {
                                promise.completeExceptionally(error);
                            } else if (result.isPresent()) {
                                if (promise.complete(result)) {
                                    log.info("{} won the race with a solution", delegate.getClass().getSimpleName());
                                }
                            } else if (isLast) {
                                promise.complete(Optional.empty());
                            }
                        });
            }
            try {
                return promise.join();
            } catch (CompletionException e) {
                throw (RuntimeException) e.getCause();
            } finally {
                cancellation.cancel();
            }
        }
    }
}
