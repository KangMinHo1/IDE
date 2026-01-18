package com.myide.backend.domain.compile;

import com.myide.backend.domain.compile.dto.ExecuteRequest;
import com.myide.backend.domain.compile.dto.ExecuteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController // "여기는 REST API 요청을 받는 곳입니다" 선언
@RequiredArgsConstructor
@RequestMapping("/api/compile") // 이 컨트롤러의 기본 주소
// 중요: 리액트(3000번 포트)에서 오는 요청을 허용하기 위해 CORS 설정 (나중에 보안 설정에서 다시 설정 해야함)
@CrossOrigin(origins = "*")
public class CompileController {

    private final CompileService compileService;

    //http://localhost:8080/api/compile
    @PostMapping
    public ExecuteResponse compileCode(@RequestBody ExecuteRequest request){
        log.info("코드 실행 요청 들어옴! 언어: {}", request.getLanguage());

        // 1. 서비스(엔진)에게 코드를 넘겨주고 결과를 받습니다.
        ExecuteResponse response = compileService.runCode(request);

        log.info("실행 결과: {}", response.getOutput());

        // 2. 받은 결과를 요청자(프론트/Postman)에게 돌려줍니다.
        return response;
    }

}
