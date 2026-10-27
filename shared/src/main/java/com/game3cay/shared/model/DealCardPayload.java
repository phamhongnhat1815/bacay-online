package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record DealCardPayload(
        String roundId,
        List<Card> cards,
        int dealOrderNo
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
