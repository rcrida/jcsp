package io.github.rcrida.jcsp.constraints.nary;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A deterministic finite automaton (DFA) over an alphabet of values of type {@code T}, used to
 * define the language of an n-ary {@link RegularConstraint}. States are integers {@code 0..numStates-1};
 * {@link #transition(int, Object)} returns {@code -1} for a missing (dead) transition.
 */
public record Automaton<T>(
        int numStates,
        int initialState,
        Set<Integer> acceptingStates,
        Map<Integer, Map<T, Integer>> transitions
) {
    public Automaton {
        assert initialState >= 0 && initialState < numStates;
        assert acceptingStates.stream().allMatch(s -> s >= 0 && s < numStates);
        assert transitions.values().stream()
                .flatMap(m -> m.values().stream())
                .allMatch(s -> s >= 0 && s < numStates);
        acceptingStates = Set.copyOf(acceptingStates);
        transitions = transitions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Map.copyOf(e.getValue())
                ));
    }

    public static <T> Automaton<T> of(int numStates, int initialState,
            Set<Integer> acceptingStates, Map<Integer, Map<T, Integer>> transitions) {
        return new Automaton<>(numStates, initialState, acceptingStates, transitions);
    }

    /** Returns the next state from {@code (state, value)}, or {@code -1} if no valid transition. */
    public int transition(int state, T value) {
        var stateMap = transitions.get(state);
        if (stateMap == null) return -1;
        return stateMap.getOrDefault(value, -1);
    }
}
