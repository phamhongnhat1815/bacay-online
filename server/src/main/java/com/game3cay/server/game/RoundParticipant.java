package com.game3cay.server.game;

import java.math.BigDecimal;

public record RoundParticipant(
    long userId, String username, String displayName, BigDecimal balance) {}
