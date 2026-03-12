package org.jcsp.relations;

import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryNotEqualsRelationTest {
    static final Domain DOMAIN = new IntRangeDomain(0, 10);
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory() {};

    Variable left = VARIABLE_FACTORY.create("left", DOMAIN);
    Variable right = VARIABLE_FACTORY.create("right", DOMAIN);
    BinaryNotEqualsRelation relation;

    @BeforeEach
    void setUp() {
        relation = BinaryNotEqualsRelation.builder()
                .left(left)
                .right(right)
                .build();
    }

    @Test
    void isSatisfied_true() {
        assertThat(relation.isSatisfied(new Assignment(Map.of(left, 0, right, 1)))).isTrue();
    }

    @Test
    void isSatisfied_false() {
        assertThat(relation.isSatisfied(new Assignment(Map.of(left, 0, right, 0)))).isFalse();
    }

    @Test
    void isSatisfied_unknowns() {
        assertThat(relation.isSatisfied(new Assignment(Map.of()))).isTrue();
        assertThat(relation.isSatisfied(new Assignment(Map.of(left, 0)))).isTrue();
        assertThat(relation.isSatisfied(new Assignment(Map.of(right, 1)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(relation.toString()).isEqualTo("left != right");
    }
}
