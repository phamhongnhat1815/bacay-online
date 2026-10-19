package com.game3cay.server.bus;

import com.game3cay.server.game.CompletedRound;
import com.game3cay.shared.model.*;

public interface GameBUS {
  /** Idempotent: cùng roundId/nội dung là no-op; nội dung khác bị từ chối. */
  void saveCompleted(CompletedRound round) throws BUSException;

  PageResult<GameHistoryItem> getHistory(long userId, int page, int pageSize) throws BUSException;

  PageResult<LeaderboardItem> getLeaderboard(int page, int pageSize) throws BUSException;
}
