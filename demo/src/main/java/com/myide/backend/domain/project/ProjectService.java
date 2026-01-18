package com.myide.backend.domain.project;

import com.myide.backend.domain.project.dto.FileNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ProjectService {

    // 모든 사용자의 작업공간이 저장될 루트 폴더
    private final String WORKSPACE_ROOT = "./user-workspaces";

    // 1. 프로젝트 생성 (폴더 만들기)
    public void createProject(String projectId, String language) {
        try {
            Path projectPath = Paths.get(WORKSPACE_ROOT, projectId);
            if (Files.exists(projectPath)) {
                throw new RuntimeException("이미 존재하는 프로젝트입니다.");
            }

            Files.createDirectories(projectPath);

            // [핵심 로직] 언어별 템플릿 생성
            createTemplateFiles(projectPath, language);

        } catch (IOException e) {
            throw new RuntimeException("프로젝트 생성 실패", e);
        }
    }

    // 언어에 따라 초기 파일 내용을 다르게 작성하는 메서드
    private void createTemplateFiles(Path projectPath, String language) throws IOException {
        String fileName;
        String content;

        switch (language.toLowerCase()) {
            case "java":
                // 자바는 src 폴더 안에 Main.java가 있는 게 국룰
                Path srcPath = projectPath.resolve("src");
                Files.createDirectories(srcPath);

                fileName = "src/Main.java";
                content = "public class Main {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.out.println(\"Hello Java World!\");\n" +
                        "    }\n" +
                        "}";
                break;

            case "python":
                fileName = "main.py";
                content = "print('Hello Python World!')";
                break;

            case "javascript":
                fileName = "index.js";
                content = "console.log('Hello Node.js World!');";
                break;

            case "cpp":
                fileName = "main.cpp";
                content = "#include <iostream>\n\n" +
                        "int main() {\n" +
                        "    std::cout << \"Hello C++ World!\" << std::endl;\n" +
                        "    return 0;\n" +
                        "}";
                break;

            case "csharp":
                // [중요] C#은 소스코드(.cs) + 프로젝트 파일(.csproj)이 같이 있어야 실행됨!
                fileName = "Program.cs";
                content = "Console.WriteLine(\"Hello C# World!\");";

                // .csproj 파일 별도 생성 (이게 없으면 dotnet run 안됨)
                String csprojContent = "<Project Sdk=\"Microsoft.NET.Sdk\">\n" +
                        "  <PropertyGroup>\n" +
                        "    <OutputType>Exe</OutputType>\n" +
                        "    <TargetFramework>net7.0</TargetFramework>\n" +
                        "    <ImplicitUsings>enable</ImplicitUsings>\n" +
                        "    <Nullable>enable</Nullable>\n" +
                        "  </PropertyGroup>\n" +
                        "</Project>";
                Files.writeString(projectPath.resolve("MyProject.csproj"), csprojContent);
                break;

            default:
                // 언어 선택 안 했을 때 기본값
                fileName = "readme.txt";
                content = "지원하지 않는 언어거나 언어가 선택되지 않았습니다.";
                break;
        }

        // 실제 파일 생성
        Path filePath = projectPath.resolve(fileName);
        Files.writeString(filePath, content);
    }

    // 2. 파일 트리 조회 (VS Code 왼쪽 탐색기용 데이터)
    public List<FileNode> getFileTree(String projectId) {
        Path rootPath = Paths.get(WORKSPACE_ROOT, projectId);
        if (!Files.exists(rootPath)) return new ArrayList<>();

        return getFileNodes(rootPath, projectId);
    }

    // 재귀적으로 폴더 탐색
    private List<FileNode> getFileNodes(Path dir, String projectId) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(path -> {
                File file = path.toFile();
                boolean isDirectory = file.isDirectory();
                List<FileNode> children = isDirectory ? getFileNodes(path, projectId) : null;

                // 프로젝트 루트로부터의 상대 경로 (예: src/Main.java)
                String relativePath = Paths.get(WORKSPACE_ROOT, projectId).relativize(path).toString();
                // 윈도우 역슬래시 이슈 방지
                relativePath = relativePath.replace("\\", "/");

                return new FileNode(file.getName(), isDirectory, relativePath, children);
            }).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("파일 목록 읽기 실패", e);
            return new ArrayList<>();
        }
    }

    // 3. 파일 내용 읽기 (클릭했을 때 코드 보여주기)
    public String readFile(String projectId, String path) {
        try {
            Path filePath = Paths.get(WORKSPACE_ROOT, projectId, path);
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패", e);
        }
    }

    // 4. 파일 저장/수정 (Ctrl+S 눌렀을 때)
    public void saveFile(String projectId, String path, String content) {
        try {
            Path filePath = Paths.get(WORKSPACE_ROOT, projectId, path);
            // 상위 폴더가 없으면 생성
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }

    // 5. 새 파일 또는 폴더 생성 (우클릭 -> New File)
    public void createFile(String projectId, String path, String type) {
        try {
            Path filePath = Paths.get(WORKSPACE_ROOT, projectId, path);

            if (Files.exists(filePath)) {
                throw new RuntimeException("이미 존재하는 파일/폴더입니다.");
            }

            if ("directory".equalsIgnoreCase(type)) {
                // 폴더 생성
                Files.createDirectories(filePath);
            } else {
                // 파일 생성 (부모 폴더가 없으면 에러 나니 확인 필요)
                Files.createDirectories(filePath.getParent());
                // 빈 파일 생성
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("파일 생성 실패", e);
        }
    }
}