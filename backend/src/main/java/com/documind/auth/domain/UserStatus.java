package com.documind.auth.domain;

/** Account status. DISABLED users fail authentication even with correct credentials. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
