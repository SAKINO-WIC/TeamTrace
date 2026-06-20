package com.teamtrace.backend.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 项目统一时间入口（当前约定使用 Asia/Shanghai）。
 */
public final class AppTime {

    private AppTime() {}

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }
}
