package com.myide.backend.domain.project.dto;

import lombok.Data;

@Data
public class SaveFileRequest {
    // 저장할 파일의 경로 (예: src/components/Header.js)
    private String path;

    // 파일의 내용 (사용자가 수정한 코드 전체)
    private String content;
}