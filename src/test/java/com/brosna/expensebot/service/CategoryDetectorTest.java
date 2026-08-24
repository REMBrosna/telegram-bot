package com.brosna.expensebot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryDetectorTest {

    private final CategoryDetector detector = new CategoryDetector();

    @Test
    void shouldDetectCoffee() {
        assertEquals("COFFEE", detector.detect("morning coffee"));
        assertEquals("COFFEE", detector.detect("iced latte"));
    }

    @Test
    void shouldStillDetectFood() {
        assertEquals("FOOD", detector.detect("lunch"));
    }
}
