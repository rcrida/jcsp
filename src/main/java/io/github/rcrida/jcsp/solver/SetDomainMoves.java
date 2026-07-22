package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Local-search move generation for {@link SetBoundedDomain} variables — the set-CP analogue of
 * {@link WalkSATSolver}'s boolean flip. A set domain isn't enumerable the way a {@link
 * DiscreteDomain} is (the number of subsets between {@code lowerBound} and {@code upperBound} is
 * exponential), so there's no "every candidate value" to offer via {@code .stream()}; instead this
 * generates a small neighbourhood of nearby concrete {@code Set<E>} values.
 * <p>
 * Public and placed in {@code io.github.rcrida.jcsp.solver} — rather than added to the
 * package-private {@link LocalSearchSupport} — because its consumers span both this package
 * ({@link MinConflictsSolver}, {@link TabuSearchSolver}) and the {@code solver.assignmentfactory}
 * subpackage ({@code RandomAssignmentFactory}, {@code GreedyAssignmentFactory}); package-private
 * visibility doesn't reach a subpackage. Same precedent as {@code NumericBounds}, which lives one
 * package above its own consumers for the identical reason.
 */
public final class SetDomainMoves {
    private SetDomainMoves() {}

    /**
     * Caps only the swap move, which is {@code O(|removable| * |addable|)}; add/remove are {@code
     * O(gap)} and left uncapped — the same "domain small enough to enumerate" assumption {@link
     * DiscreteDomain#stream()} already makes everywhere else in this local-search chain.
     */
    static final int MAX_SWAP_CANDIDATES = 50;

    /**
     * A random concrete value drawn from {@code domain}: {@code lowerBound} plus a random subset of
     * {@code upperBound \ lowerBound}, sized to land in {@code [minCardinality, maxCardinality]}.
     * <p>
     * Assumes {@code !domain.isEmpty()} — guaranteed at every real call site, since infeasible
     * domains are screened out by propagation before local search starts — the same precondition
     * style {@code SetIntervalDomain.of}'s own {@code assert}s use. Given that, {@code
     * SetIntervalDomain#isEmpty()}'s own contract guarantees {@code upperBound.size() >=
     * minCardinality}, which is exactly what keeps the arithmetic below from asking for more
     * elements than the gap actually has.
     */
    public static <E> Set<E> randomValue(@NonNull SetBoundedDomain<E> domain) {
        assert !domain.isEmpty() : "randomValue requires a feasible domain: " + domain;
        var gap = new ArrayList<>(undetermined(domain));
        Collections.shuffle(gap, ThreadLocalRandom.current());

        int minAddable = Math.max(0, domain.getMinCardinality() - domain.getLowerBound().size());
        int maxAddable = domain.getMaxCardinality() - domain.getLowerBound().size();
        int addable = Math.min(gap.size(), maxAddable);
        int target = minAddable + ThreadLocalRandom.current().nextInt(addable - minAddable + 1);

        var value = new HashSet<>(domain.getLowerBound());
        value.addAll(gap.subList(0, target));
        return value;
    }

    /**
     * A small, deterministic-ish set of representative values for seeding a "greedy" initial
     * assignment: {@code lowerBound} padded up to {@code minCardinality} and {@code upperBound}
     * trimmed down to {@code maxCardinality} (both using {@code domain.getComparator()} so the
     * choice is reproducible), plus {@code randomSampleSize} random draws via {@link #randomValue}.
     * Deliberately not exhaustive — see {@code GreedyAssignmentFactory}'s Javadoc for why a short
     * representative list is preferred over both full enumeration (infeasible) and a bare {@link
     * #randomValue} fallback (would make greedy seeding no better than random for exactly the
     * variable kind — tight cardinality/disjointness — that benefits most from it).
     */
    public static <E> List<Set<E>> representativeSeeds(@NonNull SetBoundedDomain<E> domain, int randomSampleSize) {
        assert !domain.isEmpty() : "representativeSeeds requires a feasible domain: " + domain;
        Set<Set<E>> seeds = new LinkedHashSet<>();
        seeds.add(minimalValue(domain));
        seeds.add(maximalValue(domain));
        for (int i = 0; i < randomSampleSize; i++) {
            seeds.add(randomValue(domain));
        }
        return List.copyOf(seeds);
    }

    /**
     * The repair-search neighbourhood of {@code current}: add one candidate element (if under
     * {@code maxCardinality}), remove one non-mandatory element (if over {@code minCardinality}),
     * or swap one out for one in (cardinality-preserving) — plus {@code current} itself. Every
     * generated value is a legal domain member by construction (containment and cardinality are
     * preserved by each move type individually), so no extra {@code domain.contains(...)} filter is
     * needed, and no two generated values can collide (add results are one larger than {@code
     * current}, remove one smaller, swap the same size).
     * <p>
     * Always includes {@code current} and never returns an empty stream, even when {@code domain}
     * is already a singleton ({@code lowerBound == upperBound}, so no move is legal) — this is
     * load-bearing for callers ({@link MinConflictsSolver}, {@link TabuSearchSolver}) that build a
     * value map or take a {@code min(...)} off this stream and would otherwise throw.
     */
    public static <E> Stream<Set<E>> neighbours(@NonNull SetBoundedDomain<E> domain, @NonNull Set<E> current) {
        List<Set<E>> results = new ArrayList<>();
        results.add(current);

        var addable = new ArrayList<>(domain.getUpperBound());
        addable.removeAll(current);
        var removable = new ArrayList<>(current);
        removable.removeAll(domain.getLowerBound());

        if (current.size() < domain.getMaxCardinality()) {
            for (E e : addable) {
                var value = new HashSet<>(current);
                value.add(e);
                results.add(value);
            }
        }
        if (current.size() > domain.getMinCardinality()) {
            for (E e : removable) {
                var value = new HashSet<>(current);
                value.remove(e);
                results.add(value);
            }
        }
        results.addAll(swapCandidates(current, removable, addable));

        return results.stream();
    }

    /**
     * Dispatches to {@link DiscreteDomain#stream()} (unchanged behaviour) or {@link #neighbours}
     * depending on domain kind — replaces the four {@code ((DiscreteDomain<T>)
     * csp.getDomain(variable)).stream()} call sites in {@link MinConflictsSolver} and {@link
     * TabuSearchSolver} with a single shared dispatcher, the same cross-cutting role {@link
     * LocalSearchSupport} plays for conflict scoring.
     */
    @SuppressWarnings("unchecked")
    public static <T> Stream<T> candidateValues(@NonNull Domain<T> domain, @NonNull T current) {
        if (domain instanceof DiscreteDomain<?> discrete) {
            return (Stream<T>) discrete.stream();
        }
        if (domain instanceof SetBoundedDomain<?> setDomain) {
            return (Stream<T>) neighbours((SetBoundedDomain<Object>) setDomain, (Set<Object>) current);
        }
        throw new IllegalStateException("No local-search move generator for domain kind: " + domain.getClass());
    }

    private static <E> Set<E> undetermined(SetBoundedDomain<E> domain) {
        var gap = new LinkedHashSet<>(domain.getUpperBound());
        gap.removeAll(domain.getLowerBound());
        return gap;
    }

    private static <E> Set<E> minimalValue(SetBoundedDomain<E> domain) {
        var value = new HashSet<>(domain.getLowerBound());
        int needed = domain.getMinCardinality() - value.size();
        if (needed > 0) {
            var gap = new ArrayList<>(undetermined(domain));
            gap.sort(domain.getComparator());
            value.addAll(gap.subList(0, needed));
        }
        return value;
    }

    private static <E> Set<E> maximalValue(SetBoundedDomain<E> domain) {
        var value = new HashSet<>(domain.getUpperBound());
        int excess = value.size() - domain.getMaxCardinality();
        if (excess > 0) {
            var gapSorted = new ArrayList<>(undetermined(domain));
            gapSorted.sort(domain.getComparator());
            for (int i = 0; i < excess; i++) {
                value.remove(gapSorted.get(gapSorted.size() - 1 - i));
            }
        }
        return value;
    }

    /**
     * The swap neighbourhood: every {@code (out, in)} pair drawn from {@code removable × addable},
     * capped at {@link #MAX_SWAP_CANDIDATES} — the only move type capped, since it's the only one
     * that's quadratic in the undetermined gap. Both lists are shuffled first so a capped result is
     * a uniform-ish sample rather than always favouring the same corner of the cross product; when
     * the full cross product is at or under the cap, shuffling changes only iteration order, not
     * which pairs are returned. No two pairs from the nested-loop iteration can coincide, since
     * {@code removable}/{@code addable} are themselves duplicate-free (built from {@code Set}s).
     */
    private static <E> List<Set<E>> swapCandidates(Set<E> current, List<E> removable, List<E> addable) {
        List<Set<E>> result = new ArrayList<>();
        if (removable.isEmpty() || addable.isEmpty()) return result;

        var shuffledRemovable = new ArrayList<>(removable);
        var shuffledAddable = new ArrayList<>(addable);
        Collections.shuffle(shuffledRemovable, ThreadLocalRandom.current());
        Collections.shuffle(shuffledAddable, ThreadLocalRandom.current());

        for (E out : shuffledRemovable) {
            for (E in : shuffledAddable) {
                if (result.size() >= MAX_SWAP_CANDIDATES) return result;
                result.add(swap(current, out, in));
            }
        }
        return result;
    }

    private static <E> Set<E> swap(Set<E> current, E out, E in) {
        var value = new HashSet<>(current);
        value.remove(out);
        value.add(in);
        return value;
    }
}
