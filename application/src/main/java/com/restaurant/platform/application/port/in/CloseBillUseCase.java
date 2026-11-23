package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.command.CloseBillCommand;
import com.restaurant.platform.application.result.InvoiceResult;

public interface CloseBillUseCase {
    InvoiceResult closeBill(CloseBillCommand command);
}