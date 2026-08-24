package com.brosna.expensebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.report")
public class ReportProperties {

    private boolean dailyEnabled = true;
    private boolean monthlyEnabled = true;

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public void setDailyEnabled(boolean dailyEnabled) {
        this.dailyEnabled = dailyEnabled;
    }

    public boolean isMonthlyEnabled() {
        return monthlyEnabled;
    }

    public void setMonthlyEnabled(boolean monthlyEnabled) {
        this.monthlyEnabled = monthlyEnabled;
    }
}
