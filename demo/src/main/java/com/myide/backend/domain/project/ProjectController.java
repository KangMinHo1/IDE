package com.myide.backend.domain.project;

import com.myide.backend.domain.project.dto.CreateFileRequest;
import com.myide.backend.domain.project.dto.CreateProjectRequest;
import com.myide.backend.domain.project.dto.FileNode;
import com.myide.backend.domain.project.dto.SaveFileRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;

    // 1. 프로젝트 생성 (테스트용: /api/projects/create?projectId=my-pjt)
    @PostMapping("/create")
    public String createProject(@RequestBody CreateProjectRequest request) {
        projectService.createProject(request.getProjectId(), request.getLanguage());
        return "프로젝트 생성 완료: " + request.getProjectId() + " (" + request.getLanguage() + ")";
    }

    // 2. 파일 트리 조회
    @GetMapping("/{projectId}/files")
    public List<FileNode> getFiles(@PathVariable String projectId) {
        return projectService.getFileTree(projectId);
    }

    // 3. 파일 내용 상세 조회
    @GetMapping("/{projectId}/file-content")
    public String getFileContent(@PathVariable String projectId, @RequestParam String path) {
        return projectService.readFile(projectId, path);
    }

    // 4. 파일 저장
    @PostMapping("/{projectId}/save")
    public String saveFile(@PathVariable String projectId, @RequestBody SaveFileRequest request) {
        projectService.saveFile(projectId, request.getPath(), request.getContent());
        return "저장 완료";
    }

    // 5. 새 파일/폴더 생성 API
    // POST /api/projects/{projectId}/files
    @PostMapping("/{projectId}/files")
    public String createFile(@PathVariable String projectId, @RequestBody CreateFileRequest request) {
        projectService.createFile(projectId, request.getPath(), request.getType());
        return "생성 완료: " + request.getPath();
    }


}