package com.myide.backend.domain.project.dto;

import lombok.Data;

@Data
public class CreateProjectRequest {
    private String projectId; // 프로젝트 이름 (예: my-first-app)
    private String language;  // 언어 선택 (예: java, python, csharp)
}