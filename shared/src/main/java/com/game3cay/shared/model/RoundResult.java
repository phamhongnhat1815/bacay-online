package com.game3cay.shared.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record RoundResult(
    String roundId,

    List<PlayerResult> playerResults,

    List<BetRecord> betHistory,

    List<SidePot> sidePots,

    String roundStatus,

    long endedAtMillis)
    implements Serializable {
  public static final int DISPLAY_SECONDS = 6;

  @Serial private static final long serialVersionUID = 1L;
}
