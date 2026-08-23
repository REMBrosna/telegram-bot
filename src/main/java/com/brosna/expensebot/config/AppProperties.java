package com.brosna.expensebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timeZone = "Asia/Phnom_Penh";
    private BigDecimal khrToUsdRate = new BigDecimal("4100");

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public BigDecimal getKhrToUsdRate() {
        return khrToUsdRate;
    }

    public void setKhrToUsdRate(BigDecimal khrToUsdRate) {
        this.khrToUsdRate = khrToUsdRate;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public BigDecimal khrToUsdRate() {
        if (khrToUsdRate == null || khrToUsdRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("app.khr-to-usd-rate must be greater than zero.");
        }

        return khrToUsdRate;
    }
}
