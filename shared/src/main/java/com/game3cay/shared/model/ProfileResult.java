package com.game3cay.shared.model;

public record ProfileResult(
    long userId,
    String username,
    String displayName,
    String email,
    String avatarUrl,
    java.math.BigDecimal balance,
    int totalGames,
    int totalWins,
    int totalLosses)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
