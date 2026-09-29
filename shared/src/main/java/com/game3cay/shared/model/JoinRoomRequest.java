package com.game3cay.shared.model;

import java.io.Serializable;

public record JoinRoomRequest(long roomId) implements Serializable {
    private static final long serialVersionUID = 1L;
}