package com.game3cay.server.game;

import com.game3cay.shared.model.*;
import java.math.BigDecimal;
import java.util.List;

public interface GameEngine {
  GameStateSnapshot initRound(
      String roundId, long roomId, List<RoundParticipant> players, BigDecimal ante)
      throws GameEngineException;

  List<Card> getPlayerCards(String roundId, long userId) throws GameEngineException;

  PlayerStateSnapshot getPlayerSnapshot(String roundId, long userId) throws GameEngineException;

  BetStateSnapshot getBetSnapshot(String roundId) throws GameEngineException;

  ActionResult handleAction(String roundId, long userId, BetAction action, BigDecimal raiseTo)
      throws GameEngineException;

  ActionResult handlePlayerLeft(String roundId, long userId) throws GameEngineException;

  GameStateSnapshot getPublicSnapshot(String roundId) throws GameEngineException;

  RoundResult getRoundResult(String roundId) throws GameEngineException;

  boolean isRoundActive(String roundId);

  void cleanupRound(String roundId);
}
