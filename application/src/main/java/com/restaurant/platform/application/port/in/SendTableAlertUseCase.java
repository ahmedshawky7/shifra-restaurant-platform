package com.restaurant.platform.application.port.in;

import com.restaurant.platform.application.command.TableAlertCommand;

public interface SendTableAlertUseCase {
    void sendAlert(TableAlertCommand command);
}