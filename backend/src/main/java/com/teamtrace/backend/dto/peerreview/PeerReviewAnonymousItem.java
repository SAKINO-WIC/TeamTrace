package com.teamtrace.backend.dto.peerreview;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PeerReviewAnonymousItem {
    Long reviewerId;
    Long revieweeId;
    String reviewerAlias;
    String revieweeAlias;
    String reviewerName;
    String revieweeName;
    BigDecimal score;
    String comment;
    LocalDateTime submittedAt;
}
