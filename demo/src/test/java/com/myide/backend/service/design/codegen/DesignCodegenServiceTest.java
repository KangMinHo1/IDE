package com.myide.backend.service.design.codegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myide.backend.dto.design.codegen.CodegenApplyRequest;
import com.myide.backend.dto.design.codegen.CodegenApplyResponse;
import com.myide.backend.dto.design.codegen.CodegenApplyResult;
import com.myide.backend.dto.design.codegen.CodegenApplySelection;
import com.myide.backend.dto.design.codegen.CodegenFileStatus;
import com.myide.backend.dto.design.codegen.CodegenFileView;
import com.myide.backend.dto.design.codegen.CodegenPreviewRequest;
import com.myide.backend.dto.design.codegen.CodegenPreviewResponse;
import com.myide.backend.dto.design.v2.ColumnV2;
import com.myide.backend.dto.design.v2.DesignModelV2;
import com.myide.backend.dto.design.v2.ErdV2;
import com.myide.backend.dto.design.v2.PointV2;
import com.myide.backend.dto.design.v2.TableV2;
import com.myide.backend.dto.ide.FileRequest;
import com.myide.backend.service.FileService;
import com.myide.backend.service.design.codegen.react.ReactApiClientGenerator;
import com.myide.backend.service.design.codegen.react.ReactPageStubGenerator;
import com.myide.backend.service.design.codegen.react.ReactRouteGenerator;
import com.myide.backend.service.design.codegen.spring.DdlGenerator;
import com.myide.backend.service.design.codegen.spring.SpringControllerDtoGenerator;
import com.myide.backend.service.design.codegen.spring.SpringEntityGenerator;
import com.myide.backend.service.design.codegen.spring.SpringRepositoryGenerator;
import com.myide.backend.service.design.doctor.DesignDoctorService;
import com.myide.backend.service.design.doctor.DesignRule;
import com.myide.backend.service.design.doctor.rules.ApiRules;
import com.myide.backend.service.design.doctor.rules.ErdRules;
import com.myide.backend.service.design.doctor.rules.RequirementRules;
import com.myide.backend.service.design.doctor.rules.ScreenRules;
import com.myide.backend.service.design.doctor.rules.TraceRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 실제로 파일을 쓰기 직전까지의 판단을 확인한다.
 *
 * 여기서 틀리면 남의 작업이 사라지거나, 깨진 설계에서 나온 코드가 프로젝트에
 * 들어간다. 생성 결과가 예쁜지보다 훨씬 비싼 실수라서 따로 검사한다.
 */
class DesignCodegenServiceTest {

    private static final String WORKSPACE = "ws-1";
    private static final String PROJECT = "board";
    private static final String BRANCH = "master";

    private FileService fileService;
    private ProjectStackDetector stackDetector;
    private DesignCodegenService service;

    @BeforeEach
    void setUp() {
        fileService = Mockito.mock(FileService.class);
        stackDetector = Mockito.mock(ProjectStackDetector.class);

        List<DesignRule> rules = List.of(
                new RequirementRules(), new ScreenRules(), new ApiRules(),
                new ErdRules(), new TraceRules());

        List<DesignCodeGenerator> generators = List.of(
                new SpringEntityGenerator(), new SpringRepositoryGenerator(),
                new SpringControllerDtoGenerator(new ObjectMapper()), new DdlGenerator(),
                new ReactRouteGenerator(), new ReactPageStubGenerator(),
                new ReactApiClientGenerator());

        service = new DesignCodegenService(generators, new DesignDoctorService(rules),
                stackDetector, fileService);

        springProject();
    }

    private void springProject() {
        Mockito.when(stackDetector.detect(WORKSPACE, PROJECT, BRANCH))
                .thenReturn(new ProjectStackDetector.Detected(
                        ProjectStack.SPRING, "com.example.board", "Spring Boot 프로젝트입니다."));
    }

    @Test
    @DisplayName("설계에 오류가 있으면 파일을 하나도 만들지 않는다")
    void blockedByDesignErrors() {
        // 기본키가 없는 표는 Entity 를 만들 수 없다. 설계 점검이 ERROR 로 잡는다.
        DesignModelV2 broken = withTable(new TableV2("tbl_x", "things", "",
                "", List.of(new ColumnV2("col_x", "name", "VARCHAR", 50,
                true, false, false, "", "")), new PointV2(0, 0)));

        CodegenPreviewResponse response = service.preview(WORKSPACE, preview(broken, ""));

        assertThat(response.files()).isEmpty();
        assertThat(response.blockedBy()).isNotEmpty();
        assertThat(response.blockedBy())
                .anyMatch(finding -> "TBL_NO_PK".equals(finding.ruleId()));
    }

    @Test
    @DisplayName("오류가 있는 설계는 적용도 막는다")
    void applyIsForbiddenWhenBlocked() {
        DesignModelV2 broken = withTable(new TableV2("tbl_x", "things", "",
                "", List.of(new ColumnV2("col_x", "name", "VARCHAR", 50,
                true, false, false, "", "")), new PointV2(0, 0)));

        ResponseStatusException error = catchThrowableOfType(
                () -> service.apply(WORKSPACE, new CodegenApplyRequest(broken, PROJECT, BRANCH, "",
                        List.of(new CodegenApplySelection("a.java", "")))),
                ResponseStatusException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Next.js 프로젝트에는 만들지 않고 이유를 알려 준다")
    void nextProjectIsNotSupported() {
        Mockito.when(stackDetector.detect(WORKSPACE, PROJECT, BRANCH))
                .thenReturn(new ProjectStackDetector.Detected(ProjectStack.NEXT, "", ""));

        CodegenPreviewResponse response =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        assertThat(response.files()).isEmpty();
        assertThat(response.blockedBy()).isEmpty();
        assertThat(response.note()).contains("Next.js");
    }

    @Test
    @DisplayName("없는 파일은 NEW, 내용이 같으면 IDENTICAL, 다르면 CONFLICT")
    void statusReflectsDisk() {
        CodegenPreviewResponse first =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        assertThat(first.files()).isNotEmpty();
        assertThat(first.files()).allMatch(file -> file.status() == CodegenFileStatus.NEW);

        // 방금 만든 내용이 이미 디스크에 있다고 해 두면 두 번째는 전부 같아야 한다.
        first.files().forEach(file ->
                Mockito.when(fileService.getFileContent(WORKSPACE, PROJECT, BRANCH, file.path()))
                        .thenReturn(file.content()));

        CodegenPreviewResponse second =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        assertThat(second.files()).allMatch(file -> file.status() == CodegenFileStatus.IDENTICAL);

        String somePath = first.files().get(0).path();
        Mockito.when(fileService.getFileContent(WORKSPACE, PROJECT, BRANCH, somePath))
                .thenReturn("사람이 직접 고친 내용");

        CodegenPreviewResponse third =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        assertThat(viewOf(third, somePath).status()).isEqualTo(CodegenFileStatus.CONFLICT);
        assertThat(viewOf(third, somePath).existingContent()).isEqualTo("사람이 직접 고친 내용");
    }

    @Test
    @DisplayName("고른 파일만 쓴다")
    void writesOnlySelectedFiles() {
        CodegenPreviewResponse preview =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        CodegenFileView target = preview.files().stream()
                .filter(file -> file.path().endsWith("User.java"))
                .findFirst()
                .orElseThrow();

        CodegenApplyResponse response = service.apply(WORKSPACE,
                new CodegenApplyRequest(CodegenFixtures.board(), PROJECT, BRANCH, "",
                        List.of(new CodegenApplySelection(target.path(), target.existingHash()))));

        assertThat(response.written()).isEqualTo(1);
        assertThat(response.failed()).isZero();

        ArgumentCaptor<FileRequest> captor = ArgumentCaptor.forClass(FileRequest.class);
        Mockito.verify(fileService).saveFile(captor.capture());

        assertThat(captor.getValue().getFilePath()).isEqualTo(target.path());
        assertThat(captor.getValue().getCode()).contains("public class User");
        assertThat(captor.getValue().getProjectName()).isEqualTo(PROJECT);
    }

    @Test
    @DisplayName("미리보기 이후에 파일이 바뀌었으면 덮어쓰지 않는다")
    void refusesToOverwriteChangedFile() {
        CodegenPreviewResponse preview =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        CodegenFileView target = preview.files().get(0);

        // 미리보기를 본 뒤 누군가 그 파일을 고쳤다.
        Mockito.when(fileService.getFileContent(WORKSPACE, PROJECT, BRANCH, target.path()))
                .thenReturn("팀원이 방금 쓴 내용");

        CodegenApplyResponse response = service.apply(WORKSPACE,
                new CodegenApplyRequest(CodegenFixtures.board(), PROJECT, BRANCH, "",
                        List.of(new CodegenApplySelection(target.path(), target.existingHash()))));

        assertThat(response.written()).isZero();
        assertThat(response.results().get(0).status())
                .isEqualTo(CodegenApplyResult.Status.CHANGED_MEANWHILE);

        Mockito.verify(fileService, Mockito.never()).saveFile(Mockito.any());
    }

    @Test
    @DisplayName("설계에서 나오지 않는 경로는 쓰지 않는다")
    void rejectsPathsThatAreNotGenerated() {
        CodegenApplyResponse response = service.apply(WORKSPACE,
                new CodegenApplyRequest(CodegenFixtures.board(), PROJECT, BRANCH, "",
                        List.of(new CodegenApplySelection("../../etc/passwd", ""))));

        assertThat(response.written()).isZero();
        assertThat(response.results().get(0).status())
                .isEqualTo(CodegenApplyResult.Status.FAILED);

        Mockito.verify(fileService, Mockito.never()).saveFile(Mockito.any());
    }

    @Test
    @DisplayName("이미 같은 내용이면 다시 쓰지 않는다")
    void skipsIdenticalFiles() {
        CodegenPreviewResponse preview =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));

        CodegenFileView target = preview.files().get(0);

        Mockito.when(fileService.getFileContent(WORKSPACE, PROJECT, BRANCH, target.path()))
                .thenReturn(target.content());

        CodegenPreviewResponse refreshed =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), ""));
        CodegenFileView current = viewOf(refreshed, target.path());

        CodegenApplyResponse response = service.apply(WORKSPACE,
                new CodegenApplyRequest(CodegenFixtures.board(), PROJECT, BRANCH, "",
                        List.of(new CodegenApplySelection(current.path(), current.existingHash()))));

        assertThat(response.skipped()).isEqualTo(1);
        Mockito.verify(fileService, Mockito.never()).saveFile(Mockito.any());
    }

    @Test
    @DisplayName("패키지를 직접 적으면 그 이름으로 만든다")
    void honoursRequestedPackage() {
        CodegenPreviewResponse response =
                service.preview(WORKSPACE, preview(CodegenFixtures.board(), "com.team.shop"));

        assertThat(response.basePackage()).isEqualTo("com.team.shop");
        assertThat(response.files())
                .anyMatch(file -> file.path().startsWith("src/main/java/com/team/shop/"));
        assertThat(viewOf(response, "src/main/java/com/team/shop/domain/User.java").content())
                .contains("package com.team.shop.domain;");
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private CodegenPreviewRequest preview(DesignModelV2 model, String basePackage) {
        return new CodegenPreviewRequest(model, PROJECT, BRANCH, basePackage);
    }

    private CodegenFileView viewOf(CodegenPreviewResponse response, String path) {
        return response.files().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError(path + " 이 없습니다."));
    }

    /** 기본 설계에 표 하나를 더한다. 나머지는 그대로라 무엇이 문제인지 분명해진다. */
    private DesignModelV2 withTable(TableV2 table) {
        DesignModelV2 base = CodegenFixtures.board();
        List<TableV2> tables = new java.util.ArrayList<>(base.erd().tables());
        tables.add(table);

        return new DesignModelV2(base.schemaVersion(), base.meta(), base.requirements(),
                base.screens(), base.screenTransitions(), base.apis(),
                new ErdV2(tables, base.erd().relations()));
    }
}
