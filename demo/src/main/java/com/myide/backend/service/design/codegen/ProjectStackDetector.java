package com.myide.backend.service.design.codegen;

import com.myide.backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 코드를 놓을 프로젝트가 실제로 무엇인지 디스크를 보고 판단한다.
 *
 * 사용자가 고른 값을 믿지 않는 이유가 있다. 프로젝트를 만들 때 고른 종류는
 * 어디에도 저장되지 않고, 무엇보다 <b>패키지 이름을 잘못 짚으면 생성된 자바
 * 파일이 한 개도 컴파일되지 않는다.</b> 폴더를 보면 확실히 알 수 있는 것을
 * 사람에게 묻지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectStackDetector {

    private static final Pattern GRADLE_GROUP =
            Pattern.compile("group\\s*=\\s*['\"]([A-Za-z0-9_.]+)['\"]");
    private static final Pattern MAVEN_GROUP =
            Pattern.compile("<groupId>([A-Za-z0-9_.]+)</groupId>");

    private final WorkspaceService workspaceService;

    /**
     * @param note 사람에게 보여 줄 한 줄. 왜 이렇게 판단했는지 적는다.
     */
    public record Detected(ProjectStack stack, String basePackage, String note) {
    }

    public Detected detect(String workspaceId, String projectName, String branchName) {
        Path root;

        try {
            root = workspaceService.getProjectPath(workspaceId, projectName, branchName);
        } catch (Exception e) {
            return new Detected(ProjectStack.UNKNOWN, "", "프로젝트 폴더를 찾지 못했습니다.");
        }

        if (!Files.isDirectory(root)) {
            return new Detected(ProjectStack.UNKNOWN, "",
                    "프로젝트 폴더가 아직 없습니다. 브랜치를 확인해 주세요.");
        }

        if (Files.exists(root.resolve("build.gradle"))
                || Files.exists(root.resolve("build.gradle.kts"))
                || Files.exists(root.resolve("pom.xml"))) {
            String basePackage = findBasePackage(root);
            return new Detected(ProjectStack.SPRING, basePackage,
                    "Spring Boot 프로젝트로 보입니다. 패키지: " + basePackage);
        }

        Path packageJson = root.resolve("package.json");

        if (Files.exists(packageJson)) {
            String content = readOrEmpty(packageJson);

            if (content.contains("\"next\"")
                    || Files.exists(root.resolve("next.config.js"))
                    || Files.exists(root.resolve("next.config.mjs"))
                    || Files.exists(root.resolve("next.config.ts"))) {
                return new Detected(ProjectStack.NEXT, "",
                        "Next.js 프로젝트입니다. 지금은 Next.js 용 생성기가 없습니다.");
            }

            if (content.contains("\"react\"")) {
                return new Detected(ProjectStack.REACT, "", "React 프로젝트로 보입니다.");
            }
        }

        return new Detected(ProjectStack.UNKNOWN, "",
                "Spring Boot 인지 React 인지 알아내지 못했습니다.");
    }

    /**
     * src/main/java 아래로 폴더가 하나뿐인 동안 계속 내려간다.
     *
     * com/example/demo 처럼 갈래가 없는 구간이 곧 기본 패키지다. 갈래가
     * 생기거나 자바 파일이 나오면 거기서 멈춘다.
     */
    private String findBasePackage(Path root) {
        Path javaRoot = root.resolve("src/main/java");

        if (!Files.isDirectory(javaRoot)) {
            return fromBuildFile(root);
        }

        Path current = javaRoot;
        StringBuilder parts = new StringBuilder();

        for (int depth = 0; depth < 10; depth++) {
            List<Path> children;

            try (Stream<Path> stream = Files.list(current)) {
                children = stream.toList();
            } catch (Exception e) {
                break;
            }

            List<Path> directories = children.stream().filter(Files::isDirectory).toList();
            boolean hasJavaFile = children.stream()
                    .anyMatch(path -> path.getFileName().toString().endsWith(".java"));

            if (hasJavaFile || directories.size() != 1) {
                break;
            }

            current = directories.get(0);
            if (!parts.isEmpty()) {
                parts.append(".");
            }
            parts.append(current.getFileName().toString());
        }

        String detected = parts.toString();
        return detected.isBlank() ? fromBuildFile(root) : detected;
    }

    private String fromBuildFile(Path root) {
        for (String name : List.of("build.gradle", "build.gradle.kts", "pom.xml")) {
            Path file = root.resolve(name);

            if (!Files.exists(file)) {
                continue;
            }

            String content = readOrEmpty(file);
            Matcher matcher = name.equals("pom.xml")
                    ? MAVEN_GROUP.matcher(content)
                    : GRADLE_GROUP.matcher(content);

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "com.example.demo";
    }

    private String readOrEmpty(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("프로젝트 파일을 읽지 못했습니다: {}", path);
            return "";
        }
    }
}
