package com.example.jpa.common;

import org.apache.commons.lang3.StringUtils;

public final class Texts {

    private Texts() {
        throw new AssertionError("인스턴스화할 수 없습니다.");
    }

    public static String requireText(String value, String message) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

}
