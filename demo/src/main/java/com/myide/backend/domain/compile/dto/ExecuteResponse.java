package com.myide.backend.domain.compile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecuteResponse {

    private String output; // 실행 결과 (콘솔 출력 내용)
    private String error; // 에러 메시지 (컴파일 에러 등)
}
