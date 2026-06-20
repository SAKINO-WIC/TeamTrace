package com.teamtrace.backend.domain.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightedScoreFormulasTest {

    @Test
    void peerTo100() {
        assertNull(WeightedScoreFormulas.peerAverageTo100(null, 100));
        assertEquals(new BigDecimal("50.00"), WeightedScoreFormulas.peerAverageTo100(new BigDecimal("50"), 100));
        assertEquals(new BigDecimal("100.00"), WeightedScoreFormulas.peerAverageTo100(new BigDecimal("10"), 10));
    }

    @Test
    void weightedWhenPeerNotApplicable() {
        assertNull(WeightedScoreFormulas.weightedTotal100(false, null, null, new BigDecimal("0.4"), new BigDecimal("0.6")));
        assertEquals(
                new BigDecimal("88.50"),
                WeightedScoreFormulas.weightedTotal100(
                        false, null, new BigDecimal("88.5"), new BigDecimal("0.4"), new BigDecimal("0.6")));
    }

    @Test
    void weightedWhenPeerApplicableNeedsBoth() {
        assertNull(WeightedScoreFormulas.weightedTotal100(
                true, new BigDecimal("80"), null, new BigDecimal("0.4"), new BigDecimal("0.6")));
        assertNull(WeightedScoreFormulas.weightedTotal100(
                true, null, new BigDecimal("90"), new BigDecimal("0.4"), new BigDecimal("0.6")));
        assertEquals(
                new BigDecimal("86.00"),
                WeightedScoreFormulas.weightedTotal100(
                        true, new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("0.4"), new BigDecimal("0.6")));
    }
}
