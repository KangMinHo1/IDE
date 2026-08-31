package com.myide.backend.domain.design;

import com.myide.backend.domain.User;
import com.myide.backend.domain.workspace.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 되돌리기 안전망.
 *
 * AI 초안을 적용하거나 코드를 생성하기 직전처럼 문서가 크게 바뀌는
 * 순간에 자동으로 한 벌 떠 둔다. AI가 엉뚱한 걸 잔뜩 집어넣었을 때
 * 팀 전체가 손으로 지우지 않아도 되게 하기 위한 것이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "design_doc_checkpoints")
public class DesignDocCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_uuid", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 120)
    private String label;

    @Lob
    @Column(name = "yjs_update_base64", nullable = false, columnDefinition = "LONGTEXT")
    private String yjsUpdateBase64;

    @Lob
    @Column(name = "projection_json", nullable = false, columnDefinition = "LONGTEXT")
    private String projectionJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DesignDocCheckpoint(Workspace workspace,
                               String label,
                               String yjsUpdateBase64,
                               String projectionJson,
                               User createdBy) {
        this.uuid = UUID.randomUUID().toString();
        this.workspace = workspace;
        this.label = label;
        this.yjsUpdateBase64 = yjsUpdateBase64;
        this.projectionJson = projectionJson;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
