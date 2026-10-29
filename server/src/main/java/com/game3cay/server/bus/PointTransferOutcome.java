package com.game3cay.server.bus;

import com.game3cay.shared.model.PointTransferReceipt;
import java.math.BigDecimal;

public record PointTransferOutcome(PointTransferReceipt receipt, BigDecimal senderBalance,
                                   BigDecimal recipientBalance, boolean created) {}
