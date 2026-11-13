package com.pixelflow.identity;

/** Authenticated caller resolved from the session cookie for the current request. */
public record AuthenticatedUser(String userId, String displayName) {
}
