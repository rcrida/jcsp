package io.github.rcrida.jcsp.solver.assignmentfactory;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FallbackAssignmentFactoryTest {
    @Mock ConstraintSatisfactionProblem csp;
    @Mock InitialAssignmentFactory primary;
    @Mock InitialAssignmentFactory fallback;
    @Mock Assignment primaryAssignment;
    @Mock Assignment fallbackAssignment;

    @Test
    void firstNCallsUsePrimary() {
        when(primary.getAssignment(csp)).thenReturn(primaryAssignment);
        val factory = FallbackAssignmentFactory.builder()
                .primary(primary).primaryCount(3).fallback(fallback).build();

        IntStream.range(0, 3).forEach(i -> assertThat(factory.getAssignment(csp)).isSameAs(primaryAssignment));
        verify(primary, times(3)).getAssignment(csp);
        verifyNoInteractions(fallback);
    }

    @Test
    void subsequentCallsUseFallback() {
        when(fallback.getAssignment(csp)).thenReturn(fallbackAssignment);
        val factory = FallbackAssignmentFactory.builder()
                .primary(primary).primaryCount(2).fallback(fallback).build();

        factory.getAssignment(csp);
        factory.getAssignment(csp);
        IntStream.range(0, 3).forEach(i -> assertThat(factory.getAssignment(csp)).isSameAs(fallbackAssignment));
        verify(fallback, times(3)).getAssignment(csp);
    }

    @Test
    void switchesExactlyAtBoundary() {
        when(primary.getAssignment(csp)).thenReturn(primaryAssignment);
        when(fallback.getAssignment(csp)).thenReturn(fallbackAssignment);
        val factory = FallbackAssignmentFactory.builder()
                .primary(primary).primaryCount(1).fallback(fallback).build();

        assertThat(factory.getAssignment(csp)).isSameAs(primaryAssignment);
        assertThat(factory.getAssignment(csp)).isSameAs(fallbackAssignment);
    }

    @Test
    void primaryCountZeroAlwaysUsesFallback() {
        when(fallback.getAssignment(csp)).thenReturn(fallbackAssignment);
        val factory = FallbackAssignmentFactory.builder()
                .primary(primary).primaryCount(0).fallback(fallback).build();

        assertThat(factory.getAssignment(csp)).isSameAs(fallbackAssignment);
        verifyNoInteractions(primary);
    }
}
