package com.game3cay.server.game;

import com.game3cay.shared.model.GameStateSnapshot;
import com.game3cay.shared.model.RoundResult;
import java.io.Serializable;
import java.util.List;

public record ActionResult(
    String roundId,

    GameStateSnapshot publicSnapshot,

    List<Long> playersNeedingPrivateUpdate,

    boolean roundEnded,

    RoundResult roundResult)
    implements Serializable {
  private static final long serialVersionUID = 1L;
}
