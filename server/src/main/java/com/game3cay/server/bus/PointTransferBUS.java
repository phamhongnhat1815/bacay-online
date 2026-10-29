package com.game3cay.server.bus;

import com.game3cay.shared.model.*;
import java.util.Optional;

public interface PointTransferBUS {
    PointTransferOutcome transfer(long senderId, PointTransferRequest request, long roomId) throws BUSException;
    PointRequestItem createRequest(long requesterId, PointRequestCreate request, long roomId) throws BUSException;
    PointRequestItem getRequest(long actorId, String requestId) throws BUSException;
    PointRequestResolution reply(long actorId, PointRequestReply reply) throws BUSException;
    PageResult<PointRequestItem> listRequests(long actorId, PageRequest page) throws BUSException;
    PageResult<PointTransferReceipt> history(long actorId, PageRequest page) throws BUSException;
    Optional<PointTransferReceipt> findTransfer(long senderId, String operationId) throws BUSException;
    Optional<PointRequestItem> findCreatedRequest(long requesterId, String operationId) throws BUSException;
    PointTransferReceipt getTransfer(long actorId, String transferId) throws BUSException;
}
