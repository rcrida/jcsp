package io.github.rcrida.jcsp.solver;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Solves a constraint satisfaction problem by decomposing it into independent sub-problems and finding
 * the cartesian product of their solutions. Each sub-problem's solutions are lazily computed and cached
 * in a {@link LazyList}, so they are computed at most once regardless of how many combined solutions are
 * requested. This is efficient for both finding the first solution (only the first element of each
 * sub-problem is computed) and finding all solutions (inner sub-problem solutions are replayed from cache).
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class IndependentSubproblemSolver extends SolverDecorator {
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.decomposeSubproblems()
                .map(subproblems -> {
                    log.info("Solving {} independent subproblems in parallel", subproblems.size());
                    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                        List<CompletableFuture<Optional<Assignment>>> futures = subproblems.stream()
                                .map(sub -> CompletableFuture.supplyAsync(
                                        () -> getInner().getSolution(sub), exec))
                                .toList();
                        return futures.stream()
                                .map(f -> {
                                    try {
                                        return f.join();
                                    } catch (CompletionException e) {
                                        throw (RuntimeException) e.getCause();
                                    }
                                })
                                .reduce((a1, a2) -> a1.flatMap(r1 -> a2.map(r1::merge)))
                                .orElse(Optional.empty());
                    }
                })
                .orElseGet(() -> getInner().getSolution(csp));
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return csp.decomposeSubproblems()
                .map(subproblems -> {
                    log.info("Solving {} independent subproblems", subproblems.size());
                    return subproblems.stream()
                            // solve the bigger problems first so that the smaller problems are the ones cached and replayed
                            .sorted(Comparator.comparing(ConstraintSatisfactionProblem::getSearchSpace).reversed())
                            .peek(sub -> log.info("Solving subproblem {}", sub))
                            .map(s -> new LazyList<>(getInner().getSolutions(s)))
                            .reduce((ll1, ll2) -> new LazyList<>(ll1.stream().flatMap(a1 -> ll2.stream().map(a1::merge))))
                            .map(LazyList::stream)
                            .orElse(Stream.empty());
                })
                .orElseGet(() -> getInner().getSolutions(csp));
    }
}
