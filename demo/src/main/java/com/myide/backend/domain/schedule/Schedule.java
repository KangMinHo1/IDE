package com.myide.backend.domain.schedule;

import com.myide.backend.domain.User;
import com.myide.backend.domain.workspace.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "schedules")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프론트와 API에서 사용할 공개 식별자
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_uuid", nullable = false)
    private Workspace workspace;

    // 일정 생성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // 실제 일정 담당자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private User assignee;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Schedule(
            Workspace workspace,
            User createdBy,
            User assignee,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            ScheduleStatus status,
            String category
    ) {
        this.uuid = UUID.randomUUID().toString();
        this.workspace = workspace;
        this.createdBy = createdBy;

        // 담당자가 따로 없으면 생성자가 담당자
        this.assignee = assignee != null ? assignee : createdBy;

        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status == null ? ScheduleStatus.TODO : status;
        this.category =
                category == null || category.isBlank()
                        ? "General"
                        : category;
    }

    public void updateStatus(ScheduleStatus status) {
        this.status = status;
    }

    public void updatePeriod(
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateAssignee(User assignee) {
        this.assignee = assignee;
    }

    public void updateContent(
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            ScheduleStatus status,
            String category,
            User assignee
    ) {
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.category =
                category == null || category.isBlank()
                        ? "General"
                        : category;

        if (assignee != null) {
            this.assignee = assignee;
        }
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        }

        // 혹시 서비스에서 누락돼도 생성자를 기본 담당자로
        if (this.assignee == null) {
            this.assignee = this.createdBy;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}