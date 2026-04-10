package com.campus.stats.model;

import com.campus.common.exception.BusinessException;

public enum StatsGranularity {
    DAY,
    WEEK,
    MONTH;

    public static StatsGranularity from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        try {
            return StatsGranularity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "granularity must be DAY, WEEK or MONTH");
        }
    }
}

