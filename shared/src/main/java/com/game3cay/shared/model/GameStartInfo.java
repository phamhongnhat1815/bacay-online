package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;

public record GameStartInfo(
        long roomId,
        String roundId,
        int roundNo
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
