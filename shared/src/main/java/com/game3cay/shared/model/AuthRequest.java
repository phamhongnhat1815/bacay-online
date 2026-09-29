package com.game3cay.shared.model;

import java.io.Serializable;

public record AuthRequest(String username, String password)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "AuthRequest[hidden]";
    }
}