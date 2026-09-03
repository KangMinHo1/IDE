package com.myide.backend.service.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.domain.User;
import com.myide.backend.domain.design.DesignDocCheckpoint;
import com.myide.backend.domain.design.DesignDocSnapshot;
import com.myide.backend.dto.design.v2.ApiSpecV2;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignCheckpointResponse;
import com.myide.backend.dto.design.v2.DesignMetaV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.PointV2;
import com.myide.backend.dto.design.v2.RequirementV2;
import com.myide.backend.dto.design.v2.ScreenV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.repository.UserRepository;
import com.myide.backend.repository.design.DesignApiSpecRepository;
import com.myide.backend.repository.design.DesignDocCheckpointRepository;
import com.myide.backend.repository.design.DesignDocSnapshotRepository;
import com.myide.backend.repository.design.DesignDocumentRepository;
import com.myide.backend.repository.design.DesignRequirementRepository;
import com.myide.backend.security.WorkspaceAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기록 목록에서 어느 시점인지 알아볼 수 있는지 확인한다.
 *
 * 되돌리기를 두 번 하면 "복원 직전 자동 보관"이 두 줄 쌓이는데, 라벨이
 * 같아서 어느 것으로 되돌려야 할지 알 수 없었다. 그래서 두 가지를 한다 —
 * 담긴 내용을 한 줄로 적고, <b>내용이 같으면 아예 새로 만들지 않는다.</b>
 */
class DesignCheckpointSummaryTest {

    private static final String WORKSPACE = "ws-1";
    private static final Long USER = 7L;

    private final DesignModelCodec codec = new DesignModelCodec(new ObjectMapper());

    private DesignDocSnapshotRepository snapshotRepository;
    private DesignDocCheckpointRepository checkpointRepository;
    private DesignDocSnapshot snapshot;
    private DesignSnapshotService service;

    @BeforeEach
    void setUp() {
        snapshotRepository = Mockito.mock(DesignDocSnapshotRepository.class);
        checkpointRepository = Mockito.mock(DesignDocCheckpointRepository.class);

        UserRepository userRepository = Mockito.mock(UserRepository.class);

        service = new DesignSnapshotService(
                snapshotRepository,
                checkpointRepository,
                Mockito.mock(DesignDocumentRepository.class),
                Mockito.mock(DesignRequirementRepository.class),
                Mockito.mock(DesignApiSpecRepository.class),
                userRepository,
                Mockito.mock(WorkspaceAccessGuard.class),
                codec);

        snapshot = Mockito.mock(DesignDocSnapshot.class);
        Mockito.when(snapshot.getYjsUpdateBase64()).thenReturn("AAA");
        Mockito.when(snapshotRepository.findByWorkspace_Uuid(WORKSPACE))
                .thenReturn(Optional.of(snapshot));

        Mockito.when(userRepository.findById(USER)).thenReturn(Optional.of(Mockito.mock(User.class)));
        Mockito.when(checkpointRepository.save(Mockito.any()))
                .thenAnswer(call -> call.getArgument(0));
        Mockito.when(checkpointRepository.findByWorkspace_UuidOrderByCreatedAtDesc(WORKSPACE))
                .thenReturn(List.of());
    }

    private void givenDocument(DesignModelV2 model) {
        Mockito.when(snapshot.getProjectionJson()).thenReturn(codec.toJson(model));
    }

    @Test
    @DisplayName("무엇이 담겨 있었는지 한 줄로 적어 둔다")
    void writesWhatWasInside() {
        givenDocument(board());

        service.createCheckpoint(WORKSPACE, USER, "AI 초안 적용 직전");

        ArgumentCaptor<DesignDocCheckpoint> saved =
                ArgumentCaptor.forClass(DesignDocCheckpoint.class);
        Mockito.verify(checkpointRepository).save(saved.capture());

        assertThat(saved.getValue().getSummary())
                .isEqualTo("요구사항 2 · 화면 3 · 표 1 · API 1");
    }

    @Test
    @DisplayName("빈 문서도 요약이 남는다")
    void summarizesEmptyDocument() {
        givenDocument(DesignModelV2.empty());

        service.createCheckpoint(WORKSPACE, USER, "수동 저장");

        ArgumentCaptor<DesignDocCheckpoint> saved =
                ArgumentCaptor.forClass(DesignDocCheckpoint.class);
        Mockito.verify(checkpointRepository).save(saved.capture());

        assertThat(saved.getValue().getSummary())
                .isEqualTo("요구사항 0 · 화면 0 · 표 0 · API 0");
    }

    @Test
    @DisplayName("직전 기록과 내용이 같으면 새로 만들지 않는다")
    void doesNotPileUpIdenticalRecords() {
        givenDocument(board());

        DesignDocCheckpoint latest = Mockito.mock(DesignDocCheckpoint.class);
        Mockito.when(latest.getUuid()).thenReturn("cp-1");
        Mockito.when(latest.getLabel()).thenReturn("복원 직전 자동 보관");
        Mockito.when(latest.getProjectionJson()).thenReturn(codec.toJson(board()));

        Mockito.when(checkpointRepository.findFirstByWorkspace_UuidOrderByCreatedAtDesc(WORKSPACE))
                .thenReturn(Optional.of(latest));

        DesignCheckpointResponse response =
                service.createCheckpoint(WORKSPACE, USER, "복원 직전 자동 보관");

        // 이미 있는 것을 그대로 돌려준다. 부르는 쪽은 성공으로 보면 된다.
        assertThat(response.id()).isEqualTo("cp-1");
        Mockito.verify(checkpointRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("내용이 달라지면 새로 만든다")
    void createsWhenContentChanged() {
        givenDocument(board());

        DesignDocCheckpoint latest = Mockito.mock(DesignDocCheckpoint.class);
        Mockito.when(latest.getProjectionJson())
                .thenReturn(codec.toJson(DesignModelV2.empty()));

        Mockito.when(checkpointRepository.findFirstByWorkspace_UuidOrderByCreatedAtDesc(WORKSPACE))
                .thenReturn(Optional.of(latest));

        service.createCheckpoint(WORKSPACE, USER, "AI 초안 적용 직전");

        Mockito.verify(checkpointRepository).save(Mockito.any());
    }

    /** 요구사항 2 · 화면 3 · 표 1 · API 1 짜리 문서. */
    private DesignModelV2 board() {
        RequirementV2 login = new RequirementV2("req_1", "R-01", "회원", "로그인",
                "", "must", List.of("scr_1"), List.of());
        RequirementV2 write = new RequirementV2("req_2", "R-02", "글", "글쓰기",
                "", "must", List.of("scr_2"), List.of());

        ScreenV2 one = new ScreenV2("scr_1", "/login", "로그인", "",
                "page", true, false, List.of("req_1"), List.of(), new PointV2(0, 0));
        ScreenV2 two = new ScreenV2("scr_2", "/write", "글쓰기", "",
                "page", false, true, List.of("req_2"), List.of(), new PointV2(0, 0));
        ScreenV2 three = new ScreenV2("scr_3", "/posts", "목록", "",
                "page", false, false, List.of(), List.of(), new PointV2(0, 0));

        TableV2 users = new TableV2("tbl_1", "users", "User", "",
                List.of(new ColumnV2("col_1", "id", "BIGINT", null,
                        false, true, false, "", "")), new PointV2(0, 0));

        ApiSpecV2 api = new ApiSpecV2("api_1", "GET", "/api/posts", "",
                "", "", false, "R", List.of(), List.of(), List.of("tbl_1"));

        return new DesignModelV2(2, DesignMetaV2.empty(),
                List.of(login, write), List.of(one, two, three), List.of(),
                List.of(api), new ErdV2(List.of(users), List.of()));
    }
}
