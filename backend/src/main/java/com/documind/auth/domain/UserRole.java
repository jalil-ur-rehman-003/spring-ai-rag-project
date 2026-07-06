package com.documind.auth.domain;

/** Role within an organization. Enforced both at the JWT-claim level and via method security (@PreAuthorize). */
public enum UserRole {
    ADMIN,
    EDITOR,
    VIEWER
}
