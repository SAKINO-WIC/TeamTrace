package com.teamtrace.backend.domain.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProgressFormulasTest {

    @Test
    void groupProgress() {
        assertNull(ProgressFormulas.groupProgressPercent(0, 0));
        assertEquals(new BigDecimal("50.00"), ProgressFormulas.groupProgressPercent(1, 2));
        assertEquals(new BigDecimal("100.00"), ProgressFormulas.groupProgressPercent(4, 4));
    }

    @Test
    void memberProgress() {
        assertNull(ProgressFormulas.memberProgressPercent(0, 0));
        assertEquals(new BigDecimal("33.33"), ProgressFormulas.memberProgressPercent(1, 3));
    }
}
