package com.sapio.notification;

import java.util.List;
import java.util.UUID;

import com.sapio.security.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    record Inbox(int unread, List<NotificationService.Notification> items) {
    }

    @GetMapping("/api/notifications")
    Inbox inbox(@CurrentUserId UUID userId, @RequestParam(defaultValue = "30") int limit) {
        return new Inbox(notifications.unread(userId), notifications.recent(userId, limit));
    }

    @PostMapping("/api/notifications/read")
    ResponseEntity<Void> markRead(@CurrentUserId UUID userId) {
        notifications.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
