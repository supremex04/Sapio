package com.ved.user;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

    public static final int DEFAULT_DAILY_GOAL_MINUTES = 10;
    public static final Set<Integer> ALLOWED_DAILY_GOALS = Set.of(5, 10, 15, 20, 30);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /** IANA zone id; defines the user's "local day" for streaks and analytics. */
    @Column(name = "time_zone", nullable = false)
    private String timeZone = "UTC";

    @Column(name = "daily_goal_minutes", nullable = false)
    private int dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.strip();
        this.role = role;
    }

    /** Sets the zone if it is a valid IANA id; returns whether it changed. Invalid ids are ignored. */
    public boolean updateTimeZone(String zone) {
        if (zone == null || zone.isBlank() || zone.equals(timeZone)) {
            return false;
        }
        try {
            this.timeZone = ZoneId.of(zone).getId();
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    public void setDailyGoalMinutes(int minutes) {
        if (!ALLOWED_DAILY_GOALS.contains(minutes)) {
            throw new IllegalArgumentException("Unsupported daily goal: " + minutes);
        }
        this.dailyGoalMinutes = minutes;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public int getDailyGoalMinutes() {
        return dailyGoalMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
