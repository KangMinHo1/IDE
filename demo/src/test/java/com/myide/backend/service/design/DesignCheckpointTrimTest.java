package com.myide.backend.service.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.domain.User;
import com.myide.backend.domain.design.DesignDocCheckpoint;
import com.myide.backend.domain.design.DesignDocSnapshot;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 되돌리기 기록이 끝없이 쌓이지 않는지 확인한다.
 *
 * AI 초안과 코드 생성을 적용할 때마다 자동으로 하나씩 남고, 하나하나가 설계
 * 문서 전체를 담는다. 상한이 없으면 조용히 계속 커진다. 다만 지울 때
 * <b>오래된 것</b>을 지워야 한다 — 반대로 지우면 되돌릴 수단이 사라진다.
 */
class DesignCheckpointTrimTest {

    private static final String WORKSPACE = "ws-1";
    private static final Long USER = 7L;

    private DesignDocCheckpointRepository checkpointRepository;
    private DesignSnapshotService service;

    @BeforeEach
    void setUp() {
        DesignDocSnapshotRepository snapshotRepository =
                Mockito.mock(DesignDocSnapshotRepository.class);
        checkpointRepository = Mockito.mock(DesignDocCheckpointRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        WorkspaceAccessGuard accessGuard = Mockito.mock(WorkspaceAccessGuard.class);

        service = new DesignSnapshotService(
                snapshotRepository,
                checkpointRepository,
                Mockito.mock(DesignDocumentRepository.class),
                Mockito.mock(DesignRequirementRepository.class),
                Mockito.mock(DesignApiSpecRepository.class),
                userRepository,
                accessGuard,
                new DesignModelCodec(new ObjectMapper()));

        DesignDocSnapshot snapshot = Mockito.mock(DesignDocSnapshot.class);
        Mockito.when(snapshot.getYjsUpdateBase64()).thenReturn("AAA");
        Mockito.when(snapshot.getProjectionJson()).thenReturn("{}");
        Mockito.when(snapshotRepository.findByWorkspace_Uuid(WORKSPACE))
                .thenReturn(Optional.of(snapshot));

        Mockito.when(userRepository.findById(USER)).thenReturn(Optional.of(Mockito.mock(User.class)));
        Mockito.when(checkpointRepository.save(Mockito.any()))
                .thenAnswer(call -> call.getArgument(0));
    }

    /** 최신순으로 정렬돼 오는 목록. 라벨로 몇 번째인지 알아본다. */
    private void givenCheckpoints(int count) {
        List<DesignDocCheckpoint> rows = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            DesignDocCheckpoint row = Mockito.mock(DesignDocCheckpoint.class);
            Mockito.when(row.getLabel()).thenReturn("cp-" + index);
            rows.add(row);
        }

        Mockito.when(checkpointRepository.findByWorkspace_UuidOrderByCreatedAtDesc(WORKSPACE))
                .thenReturn(rows);
    }

    @Test
    @DisplayName("기록이 상한을 넘지 않으면 아무것도 지우지 않는다")
    void keepsAllWhenUnderLimit() {
        givenCheckpoints(20);

        service.createCheckpoint(WORKSPACE, USER, "AI 초안 적용 직전");

        Mockito.verify(checkpointRepository, Mockito.never()).deleteAll(Mockito.any());
    }

    @Test
    @DisplayName("상한을 넘으면 오래된 것부터 지운다")
    void deletesOldestBeyondLimit() {
        givenCheckpoints(23);

        service.createCheckpoint(WORKSPACE, USER, "코드 생성 적용 직전");

        ArgumentCaptor<Iterable<DesignDocCheckpoint>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        Mockito.verify(checkpointRepository).deleteAll(captor.capture());

        List<String> deleted = new ArrayList<>();
        captor.getValue().forEach(row -> deleted.add(row.getLabel()));

        // 목록은 최신순이므로 뒤쪽 3개가 가장 오래된 것이다.
        assertThat(deleted).containsExactly("cp-20", "cp-21", "cp-22");
    }
}
