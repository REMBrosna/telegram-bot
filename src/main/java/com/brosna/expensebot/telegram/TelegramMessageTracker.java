package com.brosna.expensebot.telegram;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelegramMessageTracker {

    private static final int MAX_MESSAGES_PER_CHAT = 100;
    private static final Duration DELETE_WINDOW = Duration.ofHours(47);

    private final Map<Long, Deque<TrackedMessage>> messagesByChat = new ConcurrentHashMap<>();

    public void track(long chatId, int messageId) {
        if (messageId <= 0) {
            return;
        }

        Deque<TrackedMessage> messages = messagesByChat.computeIfAbsent(chatId, ignored -> new ArrayDeque<>());

        synchronized (messages) {
            messages.removeIf(message -> message.messageId() == messageId);
            messages.addLast(new TrackedMessage(messageId, Instant.now()));

            while (messages.size() > MAX_MESSAGES_PER_CHAT) {
                messages.removeFirst();
            }
        }
    }

    public List<Integer> drain(long chatId) {
        Deque<TrackedMessage> messages = messagesByChat.remove(chatId);

        if (messages == null) {
            return List.of();
        }

        synchronized (messages) {
            Instant cutoff = Instant.now().minus(DELETE_WINDOW);

            return messages.stream()
                    .filter(message -> message.trackedAt().isAfter(cutoff))
                    .map(TrackedMessage::messageId)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    private record TrackedMessage(int messageId, Instant trackedAt) {
    }
}
