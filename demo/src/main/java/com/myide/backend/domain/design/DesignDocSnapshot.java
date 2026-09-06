package com.myide.backend.domain.design;

import com.myide.backend.domain.User;
import com.myide.backend.domain.workspace.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 설계 문서 v2의 저장본. 워크스페이스당 한 행이다.
 *
 * 협업 서버(CollaborationWebSocketHandler)는 바이너리를 중계만 하고 문서를
 * 보관하지 않는다. 마지막 접속자가 나가면 방이 사라지므로, 클라이언트가
 * 주기적으로 여기에 스냅샷을 남기지 않으면 설계가 통째로 증발한다.
 *
 * 두 가지를 함께 저장한다.
 *  - yjsUpdateBase64 : 편집 복원용. Yjs 업데이트 바이너리이며 서버는 내용을
 *                      해석하지 않는다. 같은 업데이트를 여러 번 적용해도
 *                      결과가 같아서, 접속하는 모든 클라이언트가 이걸 그대로
 *                      적용해도 행이 중복되지 않는다.
 *  - projectionJson  : 소비용 평문 사본. AI 초안과 설계 닥터, 코드 생성,
 *                      최종 보고서가 읽는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "design_doc_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_design_doc_snapshot_workspace",
                        columnNames = {"workspace_uuid"}
                )
        }
)
public class DesignDocSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String uuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_uuid", nullable = false)
    private Workspace workspace;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    /** 저장될 때마다 1씩 오른다. 클라이언트가 자기 것이 반영됐는지 확인할 때 쓴다. */
    @Column(nullable = false)
    private long revision;

    @Lob
    @Column(name = "yjs_update_base64", nullable = false, columnDefinition = "LONGTEXT")
    private String yjsUpdateBase64;

    @Lob
    @Column(name = "projection_json", nullable = false, columnDefinition = "LONGTEXT")
    private String projectionJson;

    /**
     * yjsUpdateBase64 의 길이.
     * Yjs 상태는 삭제해도 흔적이 남아 사실상 줄지 않으므로, 크게 작아진
     * 스냅샷은 잘못된 문서를 덮어쓰려는 신호다. 서버가 CRDT를 몰라도
     * 쓸 수 있는 유일한 방어선이라 값으로 들고 있는다.
     */
    @Column(name = "doc_size", nullable = false)
    private long docSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DesignDocSnapshot(Workspace workspace,
                             int schemaVersion,
                             String yjsUpdateBase64,
                             String projectionJson,
                             User updatedBy) {
        this.uuid = UUID.randomUUID().toString();
        this.workspace = workspace;
        this.schemaVersion = schemaVersion;
        this.revision = 1L;
        this.yjsUpdateBase64 = yjsUpdateBase64;
        this.projectionJson = projectionJson;
        this.docSize = yjsUpdateBase64 == null ? 0 : yjsUpdateBase64.length();
        this.updatedBy = updatedBy;
    }

    public void update(int schemaVersion, String yjsUpdateBase64, String projectionJson, User updatedBy) {
        this.schemaVersion = schemaVersion;
        this.yjsUpdateBase64 = yjsUpdateBase64;
        this.projectionJson = projectionJson;
        this.docSize = yjsUpdateBase64 == null ? 0 : yjsUpdateBase64.length();
        this.updatedBy = updatedBy;
        this.revision = this.revision + 1;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
