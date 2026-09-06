package com.myide.backend.service.design.codegen;

import com.myide.backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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

    /** 패키지가 아니라 계층을 나타내는 폴더 이름. 여기까지 내려가면 안 된다. */
    private static final Set<String> LAYER_FOLDERS = Set.of(
            "domain", "entity", "model", "controller", "service", "repository", "dto",
            "config", "security", "util", "utils", "common", "handler", "mapper",
            "exception", "web", "api");

    private final WorkspaceService workspaceService;

    /**
     * @param note 사람에게 보여 줄 한 줄. 왜 이렇게 판단했는지 적는다.
     */
    public record Detected(ProjectStack stack, String basePackage, String note) {
    }

    /**
     * 프로젝트 아래에 실제로 있는 작업 폴더를 브랜치 이름으로 돌려준다.
     *
     * git 이 아는 브랜치 목록을 쓰지 않는 이유가 있다. 파일이 쓰이는 곳은
     * git 이 아니라 디스크의 폴더이고, git 을 아직 안 쓰는 프로젝트에는
     * 브랜치 목록 자체가 없다. 폴더를 보면 둘 다 해결된다.
     */
    public List<String> listBranches(String workspaceId, String projectName) {
        Path root;

        try {
            root = workspaceService.getProjectRootPath(workspaceId, projectName);
        } catch (Exception e) {
            return List.of();
        }

        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .map(workspaceService::toBranchNameFromFolderName)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.debug("작업 폴더를 읽지 못했습니다: {}", root);
            return List.of();
        }
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
     * 기본 패키지를 찾는다.
     *
     * 가장 확실한 단서는 XxxApplication.java 다. 스프링이 컴포넌트를 찾는
     * 기준이 바로 그 파일이 있는 패키지이므로, 그것이 곧 기본 패키지다.
     * 없으면 폴더가 하나뿐인 구간을 따라 내려간다.
     */
    private String findBasePackage(Path root) {
        Path javaRoot = root.resolve("src/main/java");

        if (!Files.isDirectory(javaRoot)) {
            return fromBuildFile(root);
        }

        String fromApplication = findApplicationPackage(javaRoot);
        if (!fromApplication.isBlank()) {
            return fromApplication;
        }

        String walked = walkSingleChain(javaRoot);
        return walked.isBlank() ? fromBuildFile(root) : walked;
    }

    /** XxxApplication.java 가 있는 폴더가 곧 기본 패키지다. */
    private String findApplicationPackage(Path javaRoot) {
        try (Stream<Path> stream = Files.walk(javaRoot, 12)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Application.java"))
                    .findFirst()
                    .map(path -> javaRoot.relativize(path.getParent()).toString()
                            .replace('\\', '/').replace('/', '.'))
                    .orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 폴더가 하나뿐인 동안 내려가되, 계층 폴더를 만나면 멈춘다.
     *
     * domain 하나만 만들어 둔 프로젝트에서 그냥 내려가면 domain 까지 패키지로
     * 삼아, 코드가 domain/domain/User.java 처럼 한 겹 더 깊이 들어간다.
     * 실제로 겪은 문제라 이름으로 걸러 낸다.
     */
    private String walkSingleChain(Path javaRoot) {
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

            Path next = directories.get(0);
            if (LAYER_FOLDERS.contains(next.getFileName().toString().toLowerCase())) {
                break;
            }

            current = next;
            if (!parts.isEmpty()) {
                parts.append(".");
            }
            parts.append(current.getFileName().toString());
        }

        return parts.toString();
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
