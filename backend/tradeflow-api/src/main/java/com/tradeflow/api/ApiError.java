package com.tradeflow.api;

public record ApiError(
    int status,
    String error,
    String message,
    String path
) {
}