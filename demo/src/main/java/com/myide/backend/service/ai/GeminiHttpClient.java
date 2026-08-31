package com.myide.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 큰 JSON 응답을 받아 오기 위한 Gemini 호출기.
 *
 * CoreAiService 를 쓰지 않는 이유가 분명하다. 거기에는 재시도가 없고,
 * 생성 옵션을 줄 수 없으며, 응답의 첫 조각만 읽는다. 설계 초안처럼 출력이
 * 큰 요청은 조각이 여러 개로 나뉘어 오고 429 도 자주 만나므로 그대로 무너진다.
 *
 * 최종 보고서용 GeminiReportClient 와 하는 일이 겹치지만, 그쪽은 잘 돌아가는
 * 코드라 건드리지 않았다. 나중에 보고서 쪽을 이 클라이언트로 옮기면 중복이
 * 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiHttpClient {

    private static final int MAX_RETRY_COUNT = 3;

    private final ObjectMapper objectMapper;

    @Value("${google.gemini.api-key:}")
    private String apiKey;

    @Value("${google.gemini.url:}")
    private String url;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 생성 옵션.
     *
     * responseSchema 를 주면 Gemini 가 그 모양의 JSON 만 내놓는다. 마크다운
     * 울타리나 설명문이 섞여 오는 문제가 사라져서, 파싱 안정성에 가장 크게
     * 기여하는 설정이다.
     */
    public record GenerationOptions(
            double temperature,
            double topP,
            int maxOutputTokens,
            Map<String, Object> responseSchema
    ) {
        public static GenerationOptions json(int maxOutputTokens, Map<String, Object> schema) {
            return new GenerationOptions(0.2, 0.9, maxOutputTokens, schema);
        }
    }

    /** 응답이 상한에 걸려 잘렸을 때. 호출한 쪽이 요청을 쪼개 다시 시도할 수 있다. */
    public static class ResponseTruncatedException extends RuntimeException {
        public ResponseTruncatedException(String message) {
            super(message);
        }
    }

    public static class GeminiUnavailableException extends RuntimeException {
        public GeminiUnavailableException(String message) {
            super(message);
        }

        public GeminiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && url != null && !url.isBlank();
    }

    public String generate(String prompt, GenerationOptions options) {
        if (!isConfigured()) {
            throw new GeminiUnavailableException("Gemini 설정이 없습니다.");
        }

        String requestBody = buildRequestBody(prompt, options);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                HttpResponse<String> response = send(requestBody);

                if (response.statusCode() == 200) {
                    return extractText(response.body());
                }

                if (!shouldRetry(response.statusCode())) {
                    throw new GeminiUnavailableException(
                            "AI 요청이 거부되었습니다 (" + response.statusCode() + ")");
                }

                lastError = new GeminiUnavailableException(
                        "AI 응답 실패 (" + response.statusCode() + ")");
                log.warn("⚠️ [AI] {}회차 실패: {}", attempt, response.statusCode());
            } catch (ResponseTruncatedException e) {
                // 잘린 응답은 다시 보내도 같은 결과다. 호출한 쪽이 쪼개야 한다.
                throw e;
            } catch (GeminiUnavailableException e) {
                throw e;
            } catch (Exception e) {
                lastError = new GeminiUnavailableException("AI 호출 중 오류가 발생했습니다.", e);
                log.warn("⚠️ [AI] {}회차 오류: {}", attempt, e.getMessage());
            }

            sleepBeforeRetry(attempt);
        }

        throw lastError == null
                ? new GeminiUnavailableException("AI 응답을 받지 못했습니다.")
                : lastError;
    }

    private HttpResponse<String> send(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildRequestUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * (long) Math.pow(2, attempt - 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildRequestUrl() {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return url.contains("?")
                ? url + "&key=" + encodedKey
                : url + "?key=" + encodedKey;
    }

    private String buildRequestBody(String prompt, GenerationOptions options) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", options.temperature());
        generationConfig.put("topP", options.topP());
        generationConfig.put("maxOutputTokens", options.maxOutputTokens());

        if (options.responseSchema() != null) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", options.responseSchema());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);

        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new GeminiUnavailableException("AI 요청을 만들지 못했습니다.", e);
        }
    }

    /** 응답 조각을 모두 이어 붙인다. 첫 조각만 읽으면 긴 답이 잘린다. */
    private String extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new GeminiUnavailableException("AI가 답을 내놓지 않았습니다.");
            }

            JsonNode candidate = candidates.get(0);
            String finishReason = candidate.path("finishReason").asText("");

            StringBuilder builder = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                builder.append(part.path("text").asText(""));
            }

            String result = builder.toString().trim();

            if ("MAX_TOKENS".equals(finishReason)) {
                throw new ResponseTruncatedException("AI 응답이 길이 상한에 걸려 잘렸습니다.");
            }

            if (result.isEmpty()) {
                throw new GeminiUnavailableException("AI 응답이 비어 있습니다.");
            }

            return result;
        } catch (ResponseTruncatedException | GeminiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiUnavailableException("AI 응답을 해석하지 못했습니다.", e);
        }
    }
}
