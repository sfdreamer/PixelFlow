package com.pixelflow.identity;

import java.time.Instant;

/** Flat projection of user_session joined with app_user. */
public class SessionIdentity {

    private String userId;
    private String displayName;
    private Instant expiresAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
