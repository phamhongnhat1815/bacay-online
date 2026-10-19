package com.game3cay.shared.model;

public record DealCardPayload(String roundId, java.util.List<Card> cards, long stateVersion)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
