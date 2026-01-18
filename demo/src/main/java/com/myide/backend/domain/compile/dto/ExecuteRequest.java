package com.myide.backend.domain.compile.dto;

import lombok.Data;

@Data
public class ExecuteRequest {
    private String projectId; // 예: "project-123"
    private String path;      // 실행할 파일의 경로 (예: "src/Main.java")
    private String language;  // "java", "python" 등
}