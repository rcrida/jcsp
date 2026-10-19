package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.rcrida.jcsp.constraints.nary.BitValues.bit;
import static io.github.rcrida.jcsp.constraints.nary.BitValues.valueLike;

/**
 * Pigeons and holes over boolean variables: every <em>pigeon</em> is a set of variables of which at
 * least one is true, and every <em>hole</em> a set of which at most one is. Pigeons share no
 * variable, so the variable that satisfies a pigeon is its own, and two pigeons satisfied through
 * the same hole would make two variables of it true. The pigeons therefore need distinct holes,
 * which is an all-different over pigeon-to-hole choices, and {@link #propagate} runs {@link
 * AllDiffConstraint}'s Hall-set and matching reasoning on exactly that. It refutes outright what
 * clause-by-clause reasoning cannot: ten pigeons, nine holes.
 * <p>
 * A variable may lie in several holes; it then occupies all of them, and the matching only needs one,
 * so it is weaker than the truth and never wrong. A variable with no hole of its own is given a hole
 * to itself by whoever builds the constraint.
 * <p>
 * Meant to be posted <em>in addition to</em> the clauses and at-most-one constraints it was read
 * from: it adds the counting argument and takes nothing away.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PigeonholeConstraint extends NaryConstraint implements Propagatable {

    /** Each pigeon's variables; at least one of them is true. */
    @Getter
    private final List<Set<Variable<?>>> pigeons;

    /** Each hole's variables; at most one of them is true. */
    @Getter
    private final List<Set<Variable<?>>> holes;

    /** Derived from the pigeons and holes, so not part of equality. */
    @EqualsAndHashCode.Exclude
    private final AtomicReference<Layout> layout = new AtomicReference<>();

    private record Layout(List<List<Variable<?>>> pigeonVariables, Map<Variable<?>, int[]> holesOf,
                          List<Variable<Integer>> choices, Map<Variable<?>, Integer> indexOf,
                          AllDiffConstraint<Integer> distinct) {
    }

    public static PigeonholeConstraint of(@NonNull List<Set<Variable<?>>> pigeons, @NonNull List<Set<Variable<?>>> holes) {
        assert !pigeons.isEmpty() : "a pigeonhole needs a pigeon";
        Set<Variable<?>> variables = new LinkedHashSet<>();
        for (Set<Variable<?>> pigeon : pigeons) {
            assert !pigeon.isEmpty() : "a pigeon needs a variable";
            for (Variable<?> variable : pigeon) {
                boolean added = variables.add(variable);
                assert added : "pigeons must not share a variable: " + variable;
            }
        }
        Set<Variable<?>> housed = new LinkedHashSet<>();
        holes.forEach(housed::addAll);
        assert variables.containsAll(housed) : "a hole may only hold a pigeon's variables";
        assert housed.containsAll(variables) : "every pigeon variable needs a hole";
        return PigeonholeConstraint.builder().variables(variables)
                .pigeons(copyOf(pigeons)).holes(copyOf(holes)).build();
    }

    private static List<Set<Variable<?>>> copyOf(List<Set<Variable<?>>> sets) {
        List<Set<Variable<?>>> copy = new ArrayList<>();
        sets.forEach(set -> copy.add(new LinkedHashSet<>(set)));
        return List.copyOf(copy);
    }

    private Layout layout() {
        Layout existing = layout.get();
        if (existing != null) return existing;
        Map<Variable<?>, List<Integer>> holesOf = new HashMap<>();
        for (int hole = 0; hole < holes.size(); hole++) {
            for (Variable<?> variable : holes.get(hole)) {
                holesOf.computeIfAbsent(variable, key -> new ArrayList<>()).add(hole);
            }
        }
        Map<Variable<?>, int[]> holeIndexes = new HashMap<>();
        holesOf.forEach((variable, indexes) -> holeIndexes.put(variable, indexes.stream().mapToInt(i -> i).toArray()));
        List<List<Variable<?>>> pigeonVariables = new ArrayList<>();
        List<Variable<Integer>> choices = new ArrayList<>();
        Map<Variable<?>, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < pigeons.size(); i++) {
            pigeonVariables.add(List.copyOf(pigeons.get(i)));
            Variable<Integer> choice = Variable.Factory.INSTANCE.create("pigeon" + i);
            choices.add(choice);
            indexOf.put(choice, i);
        }
        AllDiffConstraint<Integer> distinct = AllDiffConstraint.<Integer>builder().variables(new LinkedHashSet<>(choices)).build();
        layout.compareAndSet(null, new Layout(pigeonVariables, holeIndexes, choices, indexOf, distinct));
        return layout.get();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (Set<Variable<?>> pigeon : pigeons) {
            if (assignment.containsAll(pigeon) && pigeon.stream().noneMatch(v -> bit(assignment.getValueOrNull(v)) == 1)) {
                return false;
            }
        }
        for (Set<Variable<?>> hole : holes) {
            if (hole.stream().filter(assignment::isAssigned).filter(v -> bit(assignment.getValueOrNull(v)) == 1).count() > 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives each pigeon the holes its still-possible variables lie in, or only those of a variable
     * already true, and applies the all-different to those choices. A pigeon with no possible
     * variable, or a choice set with no matching, is a contradiction. A variable none of whose holes
     * its pigeon can still use is set false: the pigeon cannot be satisfied through it.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Layout layout = layout();
        Map<Variable<?>, Domain<?>> choiceDomains = new HashMap<>();
        BitSet satisfied = new BitSet();
        for (int i = 0; i < pigeons.size(); i++) {
            BitSet possible = new BitSet();
            BitSet chosen = new BitSet();
            for (Variable<?> variable : layout.pigeonVariables().get(i)) {
                DiscreteDomain<?> domain = (DiscreteDomain<?>) domains.get(variable);
                if (domain.isSingleton() && bit(domain.singleValue().orElseThrow()) == 0) continue;
                for (int hole : layout.holesOf().get(variable)) {
                    possible.set(hole);
                    if (domain.isSingleton()) chosen.set(hole);
                }
            }
            BitSet holesLeft = chosen.isEmpty() ? possible : chosen;
            if (holesLeft.isEmpty()) return Optional.empty();
            satisfied.set(i, !chosen.isEmpty());
            choiceDomains.put(layout.choices().get(i), DiscreteDomain.of(holesLeft.stream().boxed().toArray(Integer[]::new)));
        }

        Optional<Map<Variable<?>, Domain<?>>> narrowed = layout.distinct().propagate(choiceDomains);
        if (narrowed.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Map.Entry<Variable<?>, Domain<?>> entry : narrowed.get().entrySet()) {
            int pigeon = layout.indexOf().get(entry.getKey());
            if (satisfied.get(pigeon)) continue;
            BitSet holesLeft = new BitSet();
            ((DiscreteDomain<Integer>) entry.getValue()).asCollection().forEach(holesLeft::set);
            for (Variable<?> variable : layout.pigeonVariables().get(pigeon)) {
                DiscreteDomain<Object> domain = (DiscreteDomain<Object>) domains.get(variable);
                // A singleton here is false: a true one would have made its pigeon satisfied, skipped above.
                if (domain.isSingleton()) continue;
                if (usable(layout.holesOf().get(variable), holesLeft)) continue;
                Object sample = domain.asCollection().iterator().next();
                updated.put(variable, domain.toBuilder().delete(valueLike(sample, 1)).build());
            }
        }
        return Optional.of(updated);
    }

    private static boolean usable(int[] holesOfVariable, BitSet holesLeft) {
        for (int hole : holesOfVariable) {
            if (holesLeft.get(hole)) return true;
        }
        return false;
    }

    @Override
    public String getRelation() {
        return "Pigeonhole[" + pigeons.size() + " pigeons, " + holes.size() + " holes]";
    }
}
