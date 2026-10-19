package com.game3cay.server.game;

import com.game3cay.shared.model.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Chỉ dùng bên server/journal/DB; không gửi payload này cho client. */
public record CompletedRound(
    RoundResult result, long roomId, String roomName, BigDecimal ante, List<PrivateHand> hands)
    implements Serializable {
  private static final long serialVersionUID = 1L;

  public record PrivateHand(long userId, List<Card> cards) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
}
