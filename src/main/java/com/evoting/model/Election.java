package com.evoting.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "elections")
public class Election {

    /** DRAFT -> ACTIVE (voting open) -> CLOSED (voting stopped) -> PUBLISHED (result public). */
    public enum Status { DRAFT, ACTIVE, CLOSED, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime publishedAt;

    public boolean isDraft() { return status == Status.DRAFT; }
    public boolean isActive() { return status == Status.ACTIVE; }
    public boolean isClosed() { return status == Status.CLOSED; }
    public boolean isPublished() { return status == Status.PUBLISHED; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
