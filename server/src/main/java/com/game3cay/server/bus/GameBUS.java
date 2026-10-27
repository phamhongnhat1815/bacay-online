package com.game3cay.server.bus;

import com.game3cay.server.game.CompletedRound;
import com.game3cay.shared.model.GameHistoryItem;
import com.game3cay.shared.model.LeaderboardItem;
import com.game3cay.shared.model.PageResult;

public interface GameBUS {
    void saveCompleted(CompletedRound round) throws BUSException;

    PageResult<GameHistoryItem> getHistory(long userId, int page, int pageSize) throws BUSException;

    PageResult<LeaderboardItem> getLeaderboard(int page, int pageSize) throws BUSException;
}
