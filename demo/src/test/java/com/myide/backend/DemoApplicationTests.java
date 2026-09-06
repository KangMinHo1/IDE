package com.myide.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실시간 협업이 큰 변경을 나르려면 WebSocket 버퍼를 키워야 하고, 그 설정은
 * 진짜 서블릿 컨테이너가 있어야 만들어진다. 기본값인 가짜 웹 환경에서는
 * 컨테이너가 없어 이 검사가 실패하므로 실제 포트를 띄운다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
