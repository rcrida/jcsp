package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RangeNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ValueSetNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * A unary constraint enforcing {@code operand^2 op bound} -- the self-product ({@code mul(x,x)})
 * shape {@link io.github.rcrida.jcsp.parser.xcsp3} recognizes but the general-domain {@link
 * io.github.rcrida.jcsp.constraints.nary.ProductConstraint} can never represent, since its own
 * {@code Set<Variable<N>>} factor list can't hold one variable twice. Exists specifically for that
 * shape rather than generalizing {@code ProductConstraint} itself to a multiset/list of factors --
 * squaring is common enough (autocorrelation-style sum-of-squares objectives) and structurally
 * simple enough (one variable, one nonlinear but well-understood function) to warrant its own
 * dedicated propagator, the same way {@link io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint}
 * exists instead of teaching a general n-ary constraint about {@code |a-b|}.
 * <p>
 * Propagation mirrors {@link io.github.rcrida.jcsp.constraints.binary.AbsoluteDifferenceConstraint}'s
 * own operator-by-operator shape, since {@code x^2}'s achievable range from a box {@code
 * [xMin,xMax]} has the same "bounded below by zero, gap-in-the-middle for a lower threshold" shape
 * {@code |a-b|}'s does: the achievable square range is {@code [sqLo, sqHi]} where {@code sqHi =
 * max(xMin^2, xMax^2)} and {@code sqLo} is {@code 0} when the domain straddles zero (some value in
 * range squares to exactly zero), else {@code min(xMin^2, xMax^2)} (the endpoint closest to zero).
 * <ul>
 *   <li>{@link Operator#LEQ}/{@link Operator#LT}/{@link Operator#EQ}: {@code x^2 <= bound} clips
 *       {@code operand} directly to {@code [-sqrt(bound), sqrt(bound)]} -- a real interval, unlike
 *       the {@code >=} case below -- intersected with its current domain. {@code bound < 0} is
 *       immediately infeasible (a square is never negative). {@link Operator#EQ} additionally
 *       requires {@code bound} to be achievable at all ({@code sqLo <= bound <= sqHi}).</li>
 *   <li>{@link Operator#GEQ}/{@link Operator#GT}: infeasibility detection only, matching {@code
 *       AbsoluteDifferenceConstraint}'s own {@code >=}/{@code >} treatment -- {@code x^2 >= bound}
 *       excludes an open middle band around zero when {@code bound > 0}, not expressible as a single
 *       {@code [min,max]} clip on {@code operand}, so this only returns empty when no value in the
 *       current domain can reach {@code bound} ({@code sqHi < bound}), otherwise a sound no-op.
 *       {@code bound <= 0} is always satisfiable (a square is always {@code >= 0}).</li>
 *   <li>{@link Operator#NEQ}: skipped (delegated to AC3), matching every other propagator in this
 *       codebase that only narrows for the five other operators.</li>
 * </ul>
 * {@code LT}/{@code GT} are treated identically to {@code LEQ}/{@code GEQ} -- sound but not
 * maximally tight at the exact boundary, the same tradeoff {@link
 * io.github.rcrida.jcsp.constraints.nary.OrderedConstraint} and others in this codebase accept for
 * strict operators.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SquareConstraint<N extends Number> extends UnaryConstraint<N> implements Propagatable {
    @NonNull N bound;
    @NonNull Operator operator;

    public static <N extends Number> SquareConstraint<N> of(
            @NonNull Variable<N> operand, @NonNull Operator operator, @NonNull N bound) {
        return SquareConstraint.<N>builder().variable(operand).operator(operator).bound(bound).build();
    }

    @Override
    protected boolean checkValue(@NonNull N v) {
        double squared = v.doubleValue() * v.doubleValue();
        return operator.compare(squared, bound.doubleValue());
    }

    @Override
    public String getRelation() {
        return getVariable() + "^2 " + operator.symbol + " " + bound;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator == Operator.NEQ) return Optional.of(Map.of());

        Domain<N> domain = (Domain<N>) domains.get(getVariable());
        double xMin = NumericBounds.min(domain);
        double xMax = NumericBounds.max(domain);
        double b = bound.doubleValue();

        double sqHi = Math.max(xMin * xMin, xMax * xMax);
        double sqLo = (xMin <= 0 && xMax >= 0) ? 0 : Math.min(xMin * xMin, xMax * xMax);

        if (operator == Operator.GEQ || operator == Operator.GT) {
            return sqHi < b ? Optional.empty() : Optional.of(Map.of());
        }

        // LEQ, LT, EQ: x^2 <= b (a square is never negative, so b < 0 is immediately infeasible).
        if (b < 0) return Optional.empty();
        if (operator == Operator.EQ && (b < sqLo || b > sqHi)) return Optional.empty();

        double root = Math.sqrt(b);
        double newMin = Math.max(xMin, -root);
        double newMax = Math.min(xMax, root);
        if (newMin > newMax) return Optional.empty();

        Optional<Domain<N>> pruned = NumericBounds.narrow(domain, newMin, newMax);
        if (pruned.isEmpty()) return Optional.of(Map.of());
        return pruned.get().isEmpty() ? Optional.empty() : Optional.of(Map.of(getVariable(), pruned.get()));
    }

    /**
     * {@link #propagate}'s infeasibility is derived purely from {@code getVariable()}'s own current
     * domain, the same single-variable citation shape {@link UnaryComparatorConstraint#explainInfeasible}
     * uses.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains));
    }
}
