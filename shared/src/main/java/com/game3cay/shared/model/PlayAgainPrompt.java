package com.game3cay.shared.model;

public record PlayAgainPrompt(String roundId, String message) implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
