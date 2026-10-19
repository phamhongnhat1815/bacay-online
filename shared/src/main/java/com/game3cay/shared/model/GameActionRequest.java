package com.game3cay.shared.model;

public record GameActionRequest(String roundId, BetAction action, java.math.BigDecimal raiseTo)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
