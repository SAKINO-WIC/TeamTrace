package com.teamtrace.backend.domain.peerreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PeerReviewDisplayCodeTest {

    @Test
    void stablePerMember() {
        String a = PeerReviewDisplayCode.forMember(1L, 2L, 3L, "secret-x");
        String b = PeerReviewDisplayCode.forMember(1L, 2L, 3L, "secret-x");
        assertEquals(a, b);
    }

    @Test
    void differsAcrossUsers() {
        String a = PeerReviewDisplayCode.forMember(1L, 2L, 3L, "secret-x");
        String c = PeerReviewDisplayCode.forMember(1L, 2L, 4L, "secret-x");
        assertNotEquals(a, c);
    }

    @Test
    void requiresSecret() {
        assertThrows(IllegalArgumentException.class, () -> PeerReviewDisplayCode.forMember(1, 1, 1, ""));
    }
}
