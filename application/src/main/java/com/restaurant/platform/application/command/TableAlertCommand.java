package com.restaurant.platform.application.command;

import java.util.Objects;

public record TableAlertCommand(
        String tableQrToken,
        String alertType
) {
    public TableAlertCommand {
        Objects.requireNonNull(tableQrToken, "tableQrToken");
        Objects.requireNonNull(alertType, "alertType");
    }
}