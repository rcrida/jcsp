package io.github.rcrida.jcsp.constraints.nary;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/**
 * Represents a constraint in a constraint satisfaction problem (CSP) that applies
 * to a set of variables and evaluates a given predicate to determine satisfaction.
 * <p>
 * This constraint is defined by:
 * - {@code variables}: The set of variables to which the constraint applies.
 * - {@link #predicate}: A predicate that evaluates whether the
 *   provided {@link Assignment} satisfies the constraint.
 * <p>
 * The constraint is satisfied if the provided assignment passes the {@link #predicate}.
 * This allows for defining complex relationships and dependencies between multiple variables.
 * <p>
 * {@code @EqualsAndHashCode(callSuper = true)} is load-bearing, not boilerplate: without it, this
 * class would inherit {@link NaryConstraint}'s {@code variables}-only equality, so two distinct
 * {@code PredicateConstraint}s sharing a variable set (e.g. two different XCSP3 {@code <group>}-templated
 * {@code intension} rules over the same variable pair, one from each side's own perspective) would
 * compare equal and silently collapse to one in a {@code Set<Constraint>} -- confirmed as a real bug
 * via {@code RoomMate-sr0050-int.xml.lzma}, where roughly half of a "no blocking pair" stable-matching
 * encoding's constraints were being dropped this way. A {@link Predicate} has no meaningful structural
 * equality of its own (two separately-constructed closures are never {@code equals}, even if logically
 * identical), so this makes every distinct instance compare unequal instead -- conservative, but sound,
 * since nothing in this codebase ever intentionally constructs the same predicate object twice.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PredicateConstraint extends NaryConstraint {
    @NonNull private final Predicate<Assignment> predicate;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) {
            return true;
        }
        return predicate.test(assignment);
    }

    @Override
    public String getRelation() {
        return predicate.toString();
    }
}
