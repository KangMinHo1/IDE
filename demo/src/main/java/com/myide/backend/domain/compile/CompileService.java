package com.myide.backend.domain.compile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.myide.backend.domain.compile.dto.ExecuteRequest;
import com.myide.backend.domain.compile.dto.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompileService {

    private final DockerClient dockerClient;
    private final String WORKSPACE_ROOT = "./user-workspaces"; // 프로젝트들이 모여있는 곳

    public ExecuteResponse runCode(ExecuteRequest request) {
        // 1. 사용자의 프로젝트 실제 경로 찾기
        // 예: ./user-workspaces/project-123
        Path hostProjectPath = Paths.get(WORKSPACE_ROOT, request.getProjectId()).toAbsolutePath();

        String containerId = null;

        try {
            // 2. 명령어 생성 (이젠 파일 생성을 안 합니다! 이미 있으니까요)
            // request.getPath()는 "src/Main.java" 같은 상대 경로입니다.
            String command = getCommand(request.getLanguage(), request.getPath());

            // 3. 도커 실행 (프로젝트 폴더 통째로 마운트)
            containerId = createAndStartContainer(hostProjectPath, command);

            // 4. 결과 가져오기
            String logs = getContainerLogs(containerId);
            return new ExecuteResponse(logs, null);

        } catch (Exception e) {
            log.error("실행 중 에러", e);
            return new ExecuteResponse(null, "Error: " + e.getMessage());
        } finally {
            // 5. 컨테이너만 삭제 (파일은 지우면 안됨!!!)
            removeContainerOnly(containerId);
        }
    }

    private String getCommand(String language, String filePath) {
        // filePath 예시: "src/Main.java"
        switch (language.toLowerCase()) {
            case "java":
                // 자바는 패키지 구조 때문에 실행이 복잡하지만, 일단 단일 파일 실행으로 가정
                // .java를 뺀 클래스 이름 추출 등 로직이 필요할 수 있음
                // 간단하게: javac src/Main.java && java -cp src Main
                // (경로 처리는 나중에 더 정교하게 다듬어야 합니다)
                return "javac " + filePath + " && java -cp src Main";
            case "python":
                return "python3 " + filePath;
            case "javascript":
                return "node " + filePath;
            case "csharp":
                // C#은 프로젝트 단위 실행이라 `dotnet run`만 하면 됨 (경로는 프로젝트 루트)
                return "dotnet run";
            default:
                throw new IllegalArgumentException("지원하지 않는 언어입니다.");
        }
    }

    private String createAndStartContainer(Path hostProjectPath, String command) {
        String imageName = "my-ide-runner";

        // 중요: 프로젝트 폴더 전체를 /app에 마운트
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(hostProjectPath.toString(), new Volume("/app")))
                .withAutoRemove(false)
                .withMemory(512 * 1024 * 1024L) // 512MB 제한
                .withCpuCount(1L);

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withWorkingDir("/app") // 작업 위치를 프로젝트 루트로 설정
                .withCmd("sh", "-c", command)
                .exec();

        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();

        return containerId;
    }

    private String getContainerLogs(String containerId) throws InterruptedException {
        StringBuilder output = new StringBuilder();
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        output.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                    }
                })
                .awaitCompletion(10, TimeUnit.SECONDS);
        return output.toString();
    }

    private void removeContainerOnly(String containerId) {
        if (containerId != null) {
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception e) {
                log.warn("컨테이너 삭제 실패", e);
            }
        }
        // 주의: 파일 삭제(FileSystemUtils.deleteRecursively)는 절대 하면 안 됩니다!
    }
}