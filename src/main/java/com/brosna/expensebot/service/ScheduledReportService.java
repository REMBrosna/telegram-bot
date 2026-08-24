package com.brosna.expensebot.service;

import com.brosna.expensebot.config.ReportProperties;
import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Service
public class ScheduledReportService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReportService.class);

    private final ExpenseService expenseService;
    private final TelegramApiClient telegramApiClient;
    private final TelegramProperties telegramProperties;
    private final ReportProperties reportProperties;

    public ScheduledReportService(
            ExpenseService expenseService,
            TelegramApiClient telegramApiClient,
            TelegramProperties telegramProperties,
            ReportProperties reportProperties
    ) {
        this.expenseService = expenseService;
        this.telegramApiClient = telegramApiClient;
        this.telegramProperties = telegramProperties;
        this.reportProperties = reportProperties;
    }

    @Scheduled(
            cron = "${app.report.daily-cron:0 55 23 * * *}",
            zone = "${app.time-zone:Asia/Phnom_Penh}"
    )
    public void sendDailyReports() {
        if (!reportProperties.isDailyEnabled()) {
            return;
        }

        deliver("daily", expenseService::todaySummary);
    }

    @Scheduled(
            cron = "${app.report.monthly-cron:0 59 23 L * *}",
            zone = "${app.time-zone:Asia/Phnom_Penh}"
    )
    public void sendMonthlyReports() {
        if (!reportProperties.isMonthlyEnabled()) {
            return;
        }

        deliver("monthly", expenseService::monthSummary);
    }

    private void deliver(String reportName, Function<Long, String> reportFactory) {
        List<Long> recipients = configuredRecipients();

        if (recipients.isEmpty()) {
            log.warn(
                    "Skipping {} report: ALLOWED_TELEGRAM_USER_ID has no valid private-chat user ID.",
                    reportName
            );
            return;
        }

        for (Long recipientId : recipients) {
            try {
                String report = reportFactory.apply(recipientId);
                telegramApiClient.sendMessage(recipientId, report);
                log.info("Sent {} expense report to Telegram user {}.", reportName, recipientId);
            } catch (Exception ex) {
                log.error(
                        "Failed to send {} expense report to Telegram user {}.",
                        reportName,
                        recipientId,
                        ex
                );
            }
        }
    }

    private List<Long> configuredRecipients() {
        String configuredIds = telegramProperties.getAllowedUserId();

        if (configuredIds == null || configuredIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(configuredIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::parseRecipientId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Long parseRecipientId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            log.warn("Ignoring invalid Telegram recipient ID: {}", value);
            return null;
        }
    }
}
