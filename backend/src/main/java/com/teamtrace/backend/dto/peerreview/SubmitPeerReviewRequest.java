package com.teamtrace.backend.dto.peerreview;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitPeerReviewRequest {

    @NotNull
    private Long revieweeId;

    @NotNull
    private BigDecimal score;

    @Size(max = 500)
    private String comment;
}
