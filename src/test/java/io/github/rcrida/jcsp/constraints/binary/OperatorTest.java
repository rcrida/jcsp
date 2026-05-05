package io.github.rcrida.jcsp.constraints.binary;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class OperatorTest {
    @ParameterizedTest
    @CsvSource({
            "EQ,0,0,true",
            "EQ,0,1,false",
            "NEQ,0,0,false",
            "NEQ,0,1,true",
            "LT,0,0,false",
            "LT,0,1,true",
            "LT,1,0,false",
            "GT,0,0,false",
            "GT,0,1,false",
            "GT,1,0,true",
            "LEQ,0,0,true",
            "LEQ,0,1,true",
            "LEQ,1,0,false",
            "GEQ,0,0,true",
            "GEQ,0,1,false",
            "GEQ,1,0,true"
    })
    void compare(Operator operator, Object left, Object right, boolean expected) {
        assertThat(operator.compare(left, right)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "EQ,EQ",
            "NEQ,NEQ",
            "LT,GEQ",
            "GT,LEQ",
            "LEQ,GT",
            "GEQ,LT"
    })
    void reversed(Operator operator, Operator reversed) {
        assertThat(operator.reversed()).isEqualTo(reversed);
    }
}
