package com.myide.backend.service.design.codegen;

import com.myide.backend.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코드를 넣을 곳을 진짜 폴더를 보고 정하는지 확인한다.
 *
 * 여기가 틀리면 사용자는 "프로젝트를 찾지 못했습니다"만 보게 되고, 무엇을
 * 골라야 하는지 알 방법이 없다. 그래서 임시 폴더에 실제 프로젝트 모양을
 * 만들어 두고 확인한다.
 */
class ProjectStackDetectorTest {

    private static final String WORKSPACE = "ws-1";
    private static final String PROJECT = "shop";

    @TempDir
    Path root;

    private WorkspaceService workspaceService;
    private ProjectStackDetector detector;

    @BeforeEach
    void setUp() {
        workspaceService = Mockito.mock(WorkspaceService.class);
        detector = new ProjectStackDetector(workspaceService);

        Mockito.when(workspaceService.getProjectRootPath(WORKSPACE, PROJECT))
                .thenReturn(root.resolve(PROJECT));
        Mockito.when(workspaceService.toBranchNameFromFolderName(Mockito.anyString()))
                .thenAnswer(call -> call.getArgument(0));
    }

    private Path branch(String name) throws IOException {
        Path path = root.resolve(PROJECT).resolve(name);
        Files.createDirectories(path);

        Mockito.when(workspaceService.getProjectPath(WORKSPACE, PROJECT, name)).thenReturn(path);
        return path;
    }

    @Test
    @DisplayName("디스크에 있는 작업 폴더를 브랜치로 알려 준다")
    void listsBranchFoldersOnDisk() throws IOException {
        branch("master");
        branch("develop");
        Files.createDirectories(root.resolve(PROJECT).resolve(".git"));

        assertThat(detector.listBranches(WORKSPACE, PROJECT))
                .containsExactly("develop", "master");
    }

    @Test
    @DisplayName("프로젝트 폴더가 없으면 빈 목록을 준다")
    void emptyWhenProjectMissing() {
        assertThat(detector.listBranches(WORKSPACE, PROJECT)).isEmpty();
    }

    @Test
    @DisplayName("build.gradle 이 있으면 Spring Boot 로 보고 패키지를 찾아낸다")
    void detectsSpringAndPackage() throws IOException {
        Path master = branch("master");
        Files.writeString(master.resolve("build.gradle"), "group = 'com.example'",
                StandardCharsets.UTF_8);

        Path pkg = master.resolve("src/main/java/com/example/shop");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("ShopApplication.java"), "package com.example.shop;",
                StandardCharsets.UTF_8);

        ProjectStackDetector.Detected detected = detector.detect(WORKSPACE, PROJECT, "master");

        assertThat(detected.stack()).isEqualTo(ProjectStack.SPRING);
        assertThat(detected.basePackage()).isEqualTo("com.example.shop");
    }

    @Test
    @DisplayName("계층 폴더는 패키지로 삼지 않는다")
    void doesNotDescendIntoLayerFolders() throws IOException {
        Path master = branch("master");
        Files.writeString(master.resolve("build.gradle"), "group = 'com.example'",
                StandardCharsets.UTF_8);

        // 코드 생성이 한 번 돈 뒤의 모양이다. Application 파일은 없고
        // domain 폴더만 있다. 여기서 domain 까지 내려가면 다음 생성부터
        // 파일이 domain/domain/User.java 로 한 겹 더 깊이 들어간다.
        Path domain = master.resolve("src/main/java/com/example/shop/domain");
        Files.createDirectories(domain);
        Files.writeString(domain.resolve("User.java"), "package com.example.shop.domain;",
                StandardCharsets.UTF_8);

        assertThat(detector.detect(WORKSPACE, PROJECT, "master").basePackage())
                .isEqualTo("com.example.shop");
    }

    @Test
    @DisplayName("자바 폴더가 비어 있으면 build.gradle 의 group 을 쓴다")
    void fallsBackToGradleGroup() throws IOException {
        Path master = branch("master");
        Files.writeString(master.resolve("build.gradle"), "group = 'com.team.api'",
                StandardCharsets.UTF_8);

        assertThat(detector.detect(WORKSPACE, PROJECT, "master").basePackage())
                .isEqualTo("com.team.api");
    }

    @Test
    @DisplayName("package.json 의 react 를 보고 React 로 판단한다")
    void detectsReact() throws IOException {
        Path master = branch("master");
        Files.writeString(master.resolve("package.json"),
                "{\"dependencies\":{\"react\":\"^18.0.0\"}}", StandardCharsets.UTF_8);

        assertThat(detector.detect(WORKSPACE, PROJECT, "master").stack())
                .isEqualTo(ProjectStack.REACT);
    }

    @Test
    @DisplayName("Next.js 는 React 와 구분한다")
    void detectsNext() throws IOException {
        Path master = branch("master");
        Files.writeString(master.resolve("package.json"),
                "{\"dependencies\":{\"next\":\"15.0.0\",\"react\":\"^18.0.0\"}}",
                StandardCharsets.UTF_8);

        assertThat(detector.detect(WORKSPACE, PROJECT, "master").stack())
                .isEqualTo(ProjectStack.NEXT);
    }

    @Test
    @DisplayName("작업 폴더가 아직 없으면 그 사실을 알려 준다")
    void reportsMissingBranchFolder() {
        Mockito.when(workspaceService.getProjectPath(WORKSPACE, PROJECT, "master"))
                .thenReturn(root.resolve(PROJECT).resolve("master"));

        ProjectStackDetector.Detected detected = detector.detect(WORKSPACE, PROJECT, "master");

        assertThat(detected.stack()).isEqualTo(ProjectStack.UNKNOWN);
        assertThat(detected.note()).contains("폴더");
    }
}
