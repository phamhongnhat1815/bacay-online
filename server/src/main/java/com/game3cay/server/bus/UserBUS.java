package com.game3cay.server.bus;

import com.game3cay.shared.model.AdminLedgerItem;
import com.game3cay.shared.model.AdminStatsSnapshot;
import com.game3cay.shared.model.AdminUserItem;
import com.game3cay.shared.model.PageResult;
import java.math.BigDecimal;

public interface UserBUS {
    UserDTO register(String username, String rawPassword, String displayName, String email) throws BUSException;

    UserDTO authenticate(String username, String rawPassword) throws BUSException;

    UserDTO getProfile(long userId) throws BUSException;

    void updateProfile(long userId, String displayName, String email, String avatarUrl) throws BUSException;

    void changePassword(long userId, String currentRawPassword, String newRawPassword) throws BUSException;

    PageResult<AdminUserItem> findUsersForAdmin(String query, String statusFilter, int page, int pageSize) throws BUSException;

    void updateUserStatusForAdmin(long userId, boolean blocked) throws BUSException;

    void adjustBalanceForAdmin(long adminId, long targetUserId, BigDecimal delta, String reason) throws BUSException;

    AdminStatsSnapshot getSystemStatsForAdmin(long onlineUsers, int activeRooms) throws BUSException;

    PageResult<AdminLedgerItem> getLedgerForAdmin(int page, int pageSize) throws BUSException;
}
