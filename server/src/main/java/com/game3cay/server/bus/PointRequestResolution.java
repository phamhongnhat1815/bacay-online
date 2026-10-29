package com.game3cay.server.bus;

import com.game3cay.shared.model.PointRequestItem;

public record PointRequestResolution(PointRequestItem request, PointTransferOutcome transfer) {}
