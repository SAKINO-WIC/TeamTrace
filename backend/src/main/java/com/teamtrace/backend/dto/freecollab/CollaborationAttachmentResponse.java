package com.teamtrace.backend.dto.freecollab;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CollaborationAttachmentResponse {
    Long attachmentId;
    String targetType;
    Long targetId;
    String type;
    String name;
    String url;
    Long size;
    Integer sortOrder;
}
