package com.myide.backend.domain.project.dto;

import lombok.Data;

@Data
public class CreateFileRequest {
    // 생성할 위치 및 이름 (예: src/utils/Calculator.java)
    private String path;

    // 파일인지 폴더인지 구분 (file 또는 directory)
    private String type;
}