package com.restaurant.platform.application.port.out;

public interface PasswordHasherPort {
    String hash(String rawPassword);
}