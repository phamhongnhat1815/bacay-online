package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record PointRequestReply(
        String operationId, String requestId, PointRequestDecision decision
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

