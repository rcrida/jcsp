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
     * Outcome of the forward pass, shared by {@link #propagate} and {@link #explainInfeasible} so
     * the reachable-state computation lives in exactly one place. On success, {@code reachable}
     * holds the full per-position array ({@code reachable[i]} = states reachable after processing
     * positions {@code 0..i-1}); on failure, {@code reachable} is {@code null} and
     * {@code failedAt} is the position {@code i} whose domain left no live transition out of
     * {@code reachable[i]} ({@code -1} when the failure is instead detected after the loop, i.e.
     * no accepting state is reachable at the end).
     */
    private record ForwardPassResult(Set<Integer>[] reachable, int failedAt) {
        boolean failedMidSequence() { return reachable == null; }
    }

    @SuppressWarnings("unchecked")
    private ForwardPassResult forwardPass(Map<Variable<?>, Domain<?>> domains, Automaton<Object> dfa) {
        int n = sequence.size();
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
                return new ForwardPassResult(null, i);
            }
        }
        return new ForwardPassResult(reachable, -1);
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

        ForwardPassResult forward = forwardPass(domains, dfa);
        if (forward.failedMidSequence()) {
            log.debug("RegularConstraint: forward pass wipeout at position {}", forward.failedAt());
            return Optional.empty();
        }
        Set<Integer>[] reachable = forward.reachable();

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

    /**
     * Replays {@link #forwardPass} (the same helper {@link #propagate} uses) to find which of the
     * two infeasibility points was hit, then cites the positions that jointly determine it:
     * <ul>
     *   <li><b>Forward-pass wipeout</b> at position {@code i}: {@code reachable[i]} — the set of
     *       states reachable via <em>any</em> combination of values from positions
     *       {@code 0..i-1} — collapses to a single deterministic trajectory only when every one of
     *       those positions is singleton, and position {@code i} itself must be singleton too
     *       (otherwise "no value at position {@code i} has a live transition" is a domain-shape
     *       fact about the whole domain, not a single value, and can't be cited). Sound only when
     *       positions {@code 0..i} are all singleton, via {@link Propagatable#allSingletonReason}.</li>
     *   <li><b>No accepting state reachable</b>: depends on the entire sequence (the accepting-state
     *       set is a fixed automaton property, not variable-dependent), so citing every position is
     *       sound only when the whole sequence is singleton, via
     *       {@link Propagatable#allSingletonReason}.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        var dfa = (Automaton<Object>) automaton;

        ForwardPassResult forward = forwardPass(domains, dfa);
        if (forward.failedMidSequence()) {
            return Propagatable.allSingletonReason(sequence.subList(0, forward.failedAt() + 1), domains);
        }

        Set<Integer> finalReachable = new HashSet<>(forward.reachable()[sequence.size()]);
        finalReachable.retainAll(dfa.acceptingStates());
        if (finalReachable.isEmpty()) {
            return Propagatable.allSingletonReason(sequence, domains);
        }

        return Map.of();
    }

    @Override
    public String getRelation() {
        return "regular(" + sequence + ", " + automaton.numStates() + " states)";
    }
}
