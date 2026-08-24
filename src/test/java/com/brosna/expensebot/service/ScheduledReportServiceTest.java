package com.brosna.expensebot.service;

import com.brosna.expensebot.config.ReportProperties;
import com.brosna.expensebot.config.TelegramProperties;
import com.brosna.expensebot.telegram.TelegramApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledReportServiceTest {

    @Mock
    private ExpenseService expenseService;

    @Mock
    private TelegramApiClient telegramApiClient;

    private TelegramProperties telegramProperties;
    private ReportProperties reportProperties;
    private ScheduledReportService scheduledReportService;

    @BeforeEach
    void setUp() {
        telegramProperties = new TelegramProperties();
        reportProperties = new ReportProperties();
        scheduledReportService = new ScheduledReportService(
                expenseService,
                telegramApiClient,
                telegramProperties,
                reportProperties
        );
    }

    @Test
    void sendsDailyReportToEveryDistinctConfiguredRecipient() {
        telegramProperties.setAllowedUserId("100, 200, invalid, 100");
        when(expenseService.todaySummary(100L)).thenReturn("daily-100");
        when(expenseService.todaySummary(200L)).thenReturn("daily-200");

        scheduledReportService.sendDailyReports();

        verify(telegramApiClient).sendMessage(100L, "daily-100");
        verify(telegramApiClient).sendMessage(200L, "daily-200");
    }

    @Test
    void sendsMonthlyReportToConfiguredRecipient() {
        telegramProperties.setAllowedUserId("100");
        when(expenseService.monthSummary(100L)).thenReturn("monthly-100");

        scheduledReportService.sendMonthlyReports();

        verify(telegramApiClient).sendMessage(100L, "monthly-100");
    }

    @Test
    void skipsDailyReportWhenDisabled() {
        telegramProperties.setAllowedUserId("100");
        reportProperties.setDailyEnabled(false);

        scheduledReportService.sendDailyReports();

        verify(expenseService, never()).todaySummary(100L);
        verify(telegramApiClient, never()).sendMessage(100L, "daily-100");
    }

    @Test
    void skipsDeliveryWhenNoRecipientIsConfigured() {
        scheduledReportService.sendDailyReports();

        verify(expenseService, never()).todaySummary(100L);
        verify(telegramApiClient, never()).sendMessage(100L, "daily-100");
    }
}
