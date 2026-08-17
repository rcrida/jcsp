package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Requires every pair of equal-length {@code vectors} to differ in at least one position --
 * XCSP3's multi-list {@code allDifferent} ("distinct vectors") construct. Equivalent to pairwise
 * {@code OR(v1[0] != v2[0], ..., v1[m-1] != v2[m-1])} over every pair of vectors, but propagated
 * directly against each position's own domains rather than via {@code C(k,2) * m} reified
 * {@link io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint}s and
 * {@link AtLeastNConstraint}s -- the reified decomposition this constraint replaced multiplied a
 * {@code k}-vector, {@code m}-wide problem into {@code O(k^2 * m)} extra variables and
 * constraints, which dominates per-node propagation cost long before it changes what's
 * inferable; see {@code Xcsp3CallbackHandler#buildCtrAllDifferentList}.
 * <p>
 * Propagation reasons about each pair independently -- unit propagation over that pair's own
 * disjunction, forcing a value only when exactly one position remains undecided and one side is
 * singleton. It does not perform cross-pair Hall-set-style reasoning the way {@link
 * AllDiffConstraint}'s matching-based GAC does for plain scalar values, so a value can survive
 * pruning even though no completion actually uses it, in cases that only resolve by considering
 * three or more pairs jointly (e.g. length-1 vectors whose sole positions collectively form a
 * disguised scalar all-different). This is a soundness-preserving, non-regression w.r.t. the
 * reified decomposition replaced above -- that decomposition's own per-pair binary constraints hit
 * the same limit via plain arc consistency -- not a weakening introduced by this class.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DistinctVectorsConstraint extends NaryConstraint implements Propagatable {
    private final List<List<Variable<?>>> vectors;

    public static <T> DistinctVectorsConstraint of(@NonNull List<List<Variable<T>>> vectors) {
        assert vectors.size() >= 2 : "DistinctVectorsConstraint requires at least 2 vectors";
        int length = vectors.get(0).size();
        assert length > 0 : "DistinctVectorsConstraint requires non-empty vectors";
        assert vectors.stream().allMatch(v -> v.size() == length) : "DistinctVectorsConstraint requires equal-length vectors";

        var builder = DistinctVectorsConstraint.builder();
        List<List<Variable<?>>> widened = new ArrayList<>();
        for (List<Variable<T>> vector : vectors) {
            List<Variable<?>> row = new ArrayList<>(vector);
            widened.add(row);
            for (Variable<?> v : row) builder.variable(v);
        }
        return builder.vectors(widened).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (int i = 0; i < vectors.size(); i++) {
            for (int j = i + 1; j < vectors.size(); j++) {
                if (pairViolated(assignment, vectors.get(i), vectors.get(j))) return false;
            }
        }
        return true;
    }

    /** {@code true} only when every position of both vectors is assigned and all are equal. */
    private static boolean pairViolated(Assignment assignment, List<Variable<?>> a, List<Variable<?>> b) {
        boolean fullyAssigned = true;
        for (int p = 0; p < a.size(); p++) {
            var va = assignment.getValue(a.get(p));
            var vb = assignment.getValue(b.get(p));
            if (va.isEmpty() || vb.isEmpty()) {
                fullyAssigned = false;
                continue;
            }
            if (!Objects.equals(va.get(), vb.get())) return false;
        }
        return fullyAssigned;
    }

    /**
     * Outcome of classifying one pair's positions against the current domains, shared by
     * {@link #propagate} and {@link #explainInfeasible}. {@code guaranteedDiffer} means the pair
     * is already satisfied regardless of {@code ambiguousPosition} (some position's domains are
     * disjoint, so it can never end up equal there). Otherwise {@code ambiguousCount} counts
     * positions that are neither guaranteed-equal (both singleton, same value) nor
     * guaranteed-differ; {@code ambiguousPosition} holds the sole such position's index when
     * {@code ambiguousCount == 1}, {@code -1} otherwise.
     */
    private record PairClassification(boolean guaranteedDiffer, int ambiguousCount, int ambiguousPosition) {}

    private static PairClassification classify(Map<Variable<?>, Domain<?>> domains, List<Variable<?>> a, List<Variable<?>> b) {
        int ambiguousCount = 0;
        int ambiguousPosition = -1;
        for (int p = 0; p < a.size(); p++) {
            Domain<?> da = domains.get(a.get(p));
            Domain<?> db = domains.get(b.get(p));
            if (guaranteedDiffer(da, db)) return new PairClassification(true, 0, -1);
            if (!guaranteedEqual(da, db)) {
                ambiguousCount++;
                ambiguousPosition = p;
            }
        }
        return new PairClassification(false, ambiguousCount, ambiguousPosition);
    }

    @SuppressWarnings("unchecked")
    private static boolean guaranteedDiffer(Domain<?> da, Domain<?> db) {
        DiscreteDomain<Object> discreteA = (DiscreteDomain<Object>) da;
        return discreteA.toList().stream().noneMatch(db::contains);
    }

    /**
     * Only ever called after {@link #guaranteedDiffer} already returned {@code false} for the same
     * pair (see {@link #classify}), so a separate value-equality check would be dead code here: two
     * singleton domains with different values always have an empty intersection, which {@link
     * #guaranteedDiffer} would already have caught. Both singleton at this point necessarily means
     * the same value.
     */
    private static boolean guaranteedEqual(Domain<?> da, Domain<?> db) {
        return da.isSingleton() && db.isSingleton();
    }

    /**
     * Forces {@code va != vb} at the sole remaining ambiguous position by excluding the other
     * side's value, when one side is singleton -- the same narrowing generic arc consistency
     * already applies to a plain {@link io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint},
     * just computed directly instead of via a materialized constraint object. When both sides are
     * non-singleton, no specific value can be excluded from either yet, so no narrowing is possible
     * here -- returns {@link Optional#empty()}, not infeasibility.
     */
    @SuppressWarnings("unchecked")
    private static Optional<Map.Entry<Variable<?>, Domain<?>>> forceDiffer(Variable<?> va, Domain<?> da, Variable<?> vb, Domain<?> db) {
        if (da.isSingleton() && !db.isSingleton()) {
            DiscreteDomain<Object> narrowed = (DiscreteDomain<Object>) db;
            return Optional.of(Map.entry(vb, narrowed.toBuilder().delete(da.singleValue().orElseThrow()).build()));
        }
        if (db.isSingleton() && !da.isSingleton()) {
            DiscreteDomain<Object> narrowed = (DiscreteDomain<Object>) da;
            return Optional.of(Map.entry(va, narrowed.toBuilder().delete(db.singleValue().orElseThrow()).build()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < vectors.size(); i++) {
            List<Variable<?>> a = vectors.get(i);
            for (int j = i + 1; j < vectors.size(); j++) {
                List<Variable<?>> b = vectors.get(j);
                PairClassification result = classify(domains, a, b);
                if (result.guaranteedDiffer()) continue;
                if (result.ambiguousCount() == 0) return Optional.empty();
                if (result.ambiguousCount() == 1) {
                    int p = result.ambiguousPosition();
                    Variable<?> va = a.get(p), vb = b.get(p);
                    Domain<?> da = updated.getOrDefault(va, domains.get(va));
                    Domain<?> db = updated.getOrDefault(vb, domains.get(vb));
                    forceDiffer(va, da, vb, db).ifPresent(entry ->
                            updated.merge(entry.getKey(), entry.getValue(), DistinctVectorsConstraint::intersect));
                }
            }
        }
        return Optional.of(updated);
    }

    @SuppressWarnings("unchecked")
    private static Domain<?> intersect(Domain<?> existing, Domain<?> fresh) {
        DiscreteDomain<Object> existingDiscrete = (DiscreteDomain<Object>) existing;
        DiscreteDomain.Builder<Object> builder = existingDiscrete.toBuilder();
        for (Object value : existingDiscrete.toList()) {
            if (!fresh.contains(value)) builder.delete(value);
        }
        return builder.build();
    }

    /**
     * Replays {@link #classify} (the same helper {@link #propagate} uses) to find the first pair
     * with zero guaranteed-differ and zero ambiguous positions -- every position of that pair must
     * then be singleton and equal, so citing just that pair's {@code 2m} variables via
     * {@link Propagatable#allSingletonReason} is sound and, unlike citing the whole constraint's
     * variables, doesn't implicate the other {@code k - 2} vectors that played no part in the
     * violation.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        for (int i = 0; i < vectors.size(); i++) {
            List<Variable<?>> a = vectors.get(i);
            for (int j = i + 1; j < vectors.size(); j++) {
                List<Variable<?>> b = vectors.get(j);
                PairClassification result = classify(domains, a, b);
                if (!result.guaranteedDiffer() && result.ambiguousCount() == 0) {
                    List<Variable<?>> pair = new ArrayList<>(a);
                    pair.addAll(b);
                    return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(pair, domains));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        int length = vectors.isEmpty() ? 0 : vectors.get(0).size();
        return "distinctVectors(" + vectors.size() + " vectors of length " + length + ")";
    }
}
