package com.brosna.expensebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timeZone = "Asia/Phnom_Penh";

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }
}
