package com.ved.user;

public enum Role {
    STUDENT,
    TEACHER,
    ADMIN;

    /** Roles a person may pick for themselves at sign-up. ADMIN is provisioned out-of-band. */
    public boolean isSelfAssignable() {
        return this != ADMIN;
    }
}
