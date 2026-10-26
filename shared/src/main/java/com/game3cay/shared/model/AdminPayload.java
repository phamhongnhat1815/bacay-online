package com.game3cay.shared.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public final class AdminPayload {
  private AdminPayload() {}

  public enum Action {
    LIST_USERS, CREDIT, SET_BLOCKED, RESET_PASSWORD
  }

  public record Request(
      Action action,
      long userId,
      BigDecimal amount,
      String value,
      int page) implements Serializable {}

  public record Account(
      long userId,
      String username,
      String displayName,
      String role,
      String status,
      BigDecimal balance) implements Serializable {}

  public record Response(
      String message,
      List<Account> accounts,
      int page,
      int totalPages) implements Serializable {}

  public record Cards(
      long userId,
      String displayName,
      List<Card> cards) implements Serializable {}

  public record TableCards(
      long roomId,
      String roundId,
      List<Cards> players) implements Serializable {}
}