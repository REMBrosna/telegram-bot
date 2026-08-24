package com.brosna.expensebot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TelegramProperties.class,
        AppProperties.class,
        ReportProperties.class
})
public class PropertiesConfig {
}
