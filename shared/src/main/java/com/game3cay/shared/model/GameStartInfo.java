package com.game3cay.shared.model;

public record GameStartInfo(long roomId, String roundId, long stateVersion)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
