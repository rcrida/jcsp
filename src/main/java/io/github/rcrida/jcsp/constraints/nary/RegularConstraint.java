package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint requiring a sequence of variables to spell out a word accepted by a
 * deterministic finite {@link Automaton}: starting from the automaton's initial state, each
 * variable's value drives a transition, and the final state after the last variable must be
 * accepting. Equivalent to MiniZinc's {@code regular} constraint.
 * <p>
 * Propagation is full GAC via a forward-backward DP over reachable and productive automaton
 * states. For partial assignments the constraint is optimistically satisfied.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RegularConstraint extends NaryConstraint implements Propagatable {
    @Singular("sequenceVar") private final List<Variable<?>> sequence;
    private final Automaton<?> automaton;

    public static <T> RegularConstraint of(@NonNull List<Variable<T>> sequence, @NonNull Automaton<T> automaton) {
        assert !sequence.isEmpty() : "RegularConstraint requires a non-empty sequence";
        var builder = RegularConstraint.builder();
        for (var v : sequence) builder.variable(v).sequenceVar(v);
        builder.automaton(automaton);
        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        var dfa = (Automaton<Object>) automaton;
        int state = dfa.initialState();
        for (var var : sequence) {
            var value = assignment.getValue(var);
            if (value.isEmpty()) return true; // optimistic
            int next = dfa.transition(state, value.get());
            if (next == -1) return false; // invalid transition
            state = next;
        }
        return dfa.acceptingStates().contains(state);
    }

    /**
     * Generalised arc consistency via forward-backward DP. The forward pass computes the set of
     * automaton states reachable after each position; the backward pass computes the states that
     * can still reach an accepting state. A domain value at a position is supported only if it
     * connects a reachable, productive state to a productive successor; unsupported values are pruned.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = sequence.size();
        var dfa = (Automaton<Object>) automaton;

        // Forward pass: reachable[i] = states reachable after processing positions 0..i-1
        Set<Integer>[] reachable = new Set[n + 1];
        reachable[0] = Set.of(dfa.initialState());
        for (int i = 0; i < n; i++) {
            reachable[i + 1] = new HashSet<>();
            var dom = (DiscreteDomain<?>) domains.get(sequence.get(i));
            for (int q : reachable[i]) {
                for (Object v : dom.toList()) {
                    int next = dfa.transition(q, v);
                    if (next != -1) reachable[i + 1].add(next);
                }
            }
            if (reachable[i + 1].isEmpty()) {
                log.debug("RegularConstraint: forward pass wipeout at position {}", i);
                return Optional.empty();
            }
        }

        Set<Integer> finalReachable = new HashSet<>(reachable[n]);
        finalReachable.retainAll(dfa.acceptingStates());
        if (finalReachable.isEmpty()) {
            log.debug("RegularConstraint: no accepting state reachable");
            return Optional.empty();
        }

        // Backward pass: productive[i] = states at position i that can reach an accepting state
        Set<Integer>[] productive = new Set[n + 1];
        productive[n] = finalReachable;
        for (int i = n - 1; i >= 0; i--) {
            productive[i] = new HashSet<>();
            var dom = (DiscreteDomain<?>) domains.get(sequence.get(i));
            for (int q : reachable[i]) {
                for (Object v : dom.toList()) {
                    int next = dfa.transition(q, v);
                    if (next != -1 && productive[i + 1].contains(next)) {
                        productive[i].add(q);
                        break;
                    }
                }
            }
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            var dom = (DiscreteDomain<?>) domains.get(sequence.get(i));
            Set<Object> supported = new HashSet<>();
            for (int q : reachable[i]) {
                if (!productive[i].contains(q)) continue;
                for (Object v : dom.toList()) {
                    int next = dfa.transition(q, v);
                    if (next != -1 && productive[i + 1].contains(next)) supported.add(v);
                }
            }
            if (supported.size() < dom.size()) {
                var builder = ((DiscreteDomain<Object>) dom).toBuilder();
                dom.toList().stream().filter(v -> !supported.contains(v)).forEach(builder::delete);
                updated.put(sequence.get(i), builder.build());
            }
        }
        log.debug("RegularConstraint propagation: {} domain(s) pruned", updated.size());
        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "regular(" + sequence + ", " + automaton.numStates() + " states)";
    }
}
