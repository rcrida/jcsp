package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.PigeonholeConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the pigeons and holes a problem states as clauses and at-most-one constraints, and builds the
 * {@link PigeonholeConstraint}s that count them, for {@link RedundantConstraints} to add. The
 * clauses and constraints themselves stay.
 * <p>
 * A <em>pigeon</em> is a forbidden all-false tuple over boolean variables, i.e. the clause "at least
 * one is true". A <em>hole</em> is a set of variables of which at most one is true: an {@link
 * AtMostOneConstraint} (which {@link io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint}
 * extends), or a clique of the pairs a forbidden
 * {@code (true, true)} tuple rules out, grown greedily and so not necessarily maximum -- any clique
 * is a sound hole. Pigeons that share a variable cannot both be kept, so they are taken greedily in
 * name order and the later ones dropped. A variable in no hole is a hole to itself.
 * <p>
 * Pigeons connected through holes form a group, posted when it has at least {@link #MIN_PIGEONS}
 * and at most {@link #MAX_PIGEONS} pigeons and no more holes than pigeons: with more holes than
 * pigeons the counting argument has nothing to refute at the root and the matching would run at
 * every search node for little, as in a graph-colouring encoding where each edge adds holes.
 */
final class Pigeonholes {
    static final int MIN_PIGEONS = 2;
    static final int MAX_PIGEONS = 1024;
    /** Past this many pair constraints the clique search is skipped. */
    static final int MAX_PAIRS = 200_000;

    private static final Comparator<Variable<?>> BY_NAME = Comparator.comparing(Variable::getName);

    private Pigeonholes() {
    }

    /** A constraint for each group of {@code csp}'s clauses and at-most-one constraints worth counting. */
    static @NonNull List<PigeonholeConstraint> pigeonholesIn(@NonNull ConstraintSatisfactionProblem csp) {
        return pigeonholesIn(csp, MAX_PAIRS);
    }

    /** As {@link #pigeonholesIn(ConstraintSatisfactionProblem)}, skipping the clique search past {@code maxPairs} pair constraints. */
    static @NonNull List<PigeonholeConstraint> pigeonholesIn(@NonNull ConstraintSatisfactionProblem csp, int maxPairs) {
        List<Set<Variable<?>>> clauses = new ArrayList<>();
        List<Set<Variable<?>>> cliques = new ArrayList<>();
        Map<Variable<?>, Set<Variable<?>>> pairs = new HashMap<>();
        int pairCount = 0;
        for (Constraint constraint : csp.getConstraints()) {
            if (constraint instanceof AtMostOneConstraint) {
                cliques.add(constraint.getVariables());
            } else if (constraint instanceof NaryConflictTuplesConstraint table && table.getConflicts().size() == 1) {
                Assignment tuple = table.getConflicts().iterator().next();
                Set<Variable<?>> variables = tuple.variables();
                if (variables.size() < 2) continue;
                if (allEqualTo(tuple, 0)) {
                    if (variables.stream().allMatch(v -> isBitDomain(csp.getVariableDomains().get(v)))) clauses.add(variables);
                } else if (variables.size() == 2 && allEqualTo(tuple, 1) && ++pairCount <= maxPairs) {
                    Variable<?> first = variables.iterator().next();
                    Variable<?> second = variables.stream().filter(v -> v != first).findFirst().orElseThrow();
                    pairs.computeIfAbsent(first, key -> new HashSet<>()).add(second);
                    pairs.computeIfAbsent(second, key -> new HashSet<>()).add(first);
                }
            }
        }
        if (pairCount <= maxPairs) cliques.addAll(cliquesOf(pairs));
        return groupsOf(disjoint(clauses), cliques);
    }

    /**
     * Whether {@code domain} holds only bits. A clause is satisfied by any value but 0, and the
     * counting treats that as "true", so a variable that could take a third value is not a pigeon's.
     */
    static boolean isBitDomain(Domain<?> domain) {
        return domain instanceof DiscreteDomain<?> discrete
                && discrete.asCollection().stream().allMatch(value -> ParitySystems.bitOf(value) >= 0);
    }

    private static boolean allEqualTo(Assignment tuple, int bit) {
        for (Variable<?> variable : tuple.variables()) {
            if (ParitySystems.bitOf(tuple.getValueOrNull(variable)) != bit) return false;
        }
        return true;
    }

    /** A maximal clique through every variable, grown greedily over its neighbours in name order. */
    static List<Set<Variable<?>>> cliquesOf(Map<Variable<?>, Set<Variable<?>>> neighbours) {
        Set<Set<Variable<?>>> cliques = new LinkedHashSet<>();
        for (Variable<?> start : sorted(neighbours.keySet())) {
            Set<Variable<?>> clique = new LinkedHashSet<>(List.of(start));
            for (Variable<?> candidate : sorted(neighbours.get(start))) {
                if (clique.stream().allMatch(member -> neighbours.get(member).contains(candidate))) clique.add(candidate);
            }
            cliques.add(clique);
        }
        return List.copyOf(cliques);
    }

    private static List<Variable<?>> sorted(Set<Variable<?>> variables) {
        return variables.stream().sorted(BY_NAME).toList();
    }

    /** The clauses, in name order, that share no variable with an earlier one. */
    static List<Set<Variable<?>>> disjoint(List<Set<Variable<?>>> clauses) {
        List<Set<Variable<?>>> ordered = new ArrayList<>(clauses);
        ordered.sort(Comparator.comparing(clause -> String.join(",", sorted(clause).stream().map(Variable::getName).toList())));
        List<Set<Variable<?>>> kept = new ArrayList<>();
        Set<Variable<?>> used = new HashSet<>();
        for (Set<Variable<?>> clause : ordered) {
            if (clause.stream().noneMatch(used::contains)) {
                kept.add(clause);
                used.addAll(clause);
            }
        }
        return kept;
    }

    /** One constraint per group of {@code pigeons} that {@code cliques} connect, if the group is worth posting. */
    static List<PigeonholeConstraint> groupsOf(List<Set<Variable<?>>> pigeons, List<Set<Variable<?>>> cliques) {
        Map<Variable<?>, Integer> pigeonOf = new HashMap<>();
        for (int i = 0; i < pigeons.size(); i++) {
            for (Variable<?> variable : pigeons.get(i)) pigeonOf.put(variable, i);
        }
        Set<Set<Variable<?>>> holes = new LinkedHashSet<>();
        for (Set<Variable<?>> clique : cliques) {
            Set<Variable<?>> inPigeons = new LinkedHashSet<>();
            clique.stream().filter(pigeonOf::containsKey).forEach(inPigeons::add);
            if (!inPigeons.isEmpty()) holes.add(inPigeons);
        }
        Set<Variable<?>> housed = new HashSet<>();
        holes.forEach(housed::addAll);
        for (Set<Variable<?>> pigeon : pigeons) {
            for (Variable<?> variable : pigeon) {
                if (!housed.contains(variable)) holes.add(new LinkedHashSet<>(List.of(variable)));
            }
        }

        int[] parent = new int[pigeons.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (Set<Variable<?>> hole : holes) {
            int first = -1;
            for (Variable<?> variable : hole) {
                int pigeon = pigeonOf.get(variable);
                if (first < 0) first = pigeon; else parent[root(parent, pigeon)] = root(parent, first);
            }
        }
        Map<Integer, List<Set<Variable<?>>>> pigeonGroups = new LinkedHashMap<>();
        for (int i = 0; i < pigeons.size(); i++) {
            pigeonGroups.computeIfAbsent(root(parent, i), key -> new ArrayList<>()).add(pigeons.get(i));
        }
        Map<Integer, List<Set<Variable<?>>>> holeGroups = new LinkedHashMap<>();
        for (Set<Variable<?>> hole : holes) {
            holeGroups.computeIfAbsent(root(parent, pigeonOf.get(hole.iterator().next())), key -> new ArrayList<>()).add(hole);
        }
        List<PigeonholeConstraint> constraints = new ArrayList<>();
        pigeonGroups.forEach((group, members) -> {
            List<Set<Variable<?>>> groupHoles = holeGroups.get(group);
            if (members.size() >= MIN_PIGEONS && members.size() <= MAX_PIGEONS && groupHoles.size() <= members.size()) {
                constraints.add(PigeonholeConstraint.of(members, groupHoles));
            }
        });
        return constraints;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
