package com.umerqureshicodes.tripslice.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Python AI service, which owns every TwelveLabs call and the embeddings table.
 * Failures return null or false rather than throwing, so a service outage degrades the
 * feature instead of breaking uploads and montages.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    public static final String KIND_VIDEO = "VIDEO";
    public static final String KIND_MONTAGE = "MONTAGE";

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public AiClient(@Value("${ai.service.url}") String baseUrl, @Value("${ai.service.key}") String serviceKey) {
        this(RestClient.builder()
                .requestFactory(timeouts())
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Key", serviceKey)
                .build());
    }

    // Without timeouts a stuck AI service would hold a request thread forever.
    // Analyze calls on a long video can take a while, hence the generous read timeout
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(180_000);
        return factory;
    }

    // For tests, so calls can be pointed at a mock server
    AiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // --- TwelveLabs ---

    /** Sends a presigned url, TwelveLabs downloads the video from there. Returns its video id. */
    public String indexVideo(String videoUrl) {
        Map<String, Object> body = post("/videos/index", Map.of("video_url", videoUrl));
        return body == null ? null : (String) body.get("video_id");
    }

    /** Null while the video is still being indexed, or if it is no longer in the index. */
    public String getAssetId(String videoId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/videos/{videoId}/asset", videoId)
                    .retrieve()
                    .body(MAP);
            return body == null ? null : (String) body.get("asset_id");
        } catch (Exception e) {
            log.warn("AI service call failed (getAssetId): {}", e.getMessage());
            return null;
        }
    }

    public boolean deleteVideo(String videoId) {
        try {
            restClient.delete().uri("/videos/{videoId}", videoId).retrieve().body(MAP);
            return true;
        } catch (Exception e) {
            log.warn("AI service call failed (deleteVideo): {}", e.getMessage());
            return false;
        }
    }

    public String summarize(String assetId) {
        Map<String, Object> body = post("/videos/{assetId}/summary", Map.of(), assetId);
        return body == null ? null : (String) body.get("summary");
    }

    /** Raw interval text, e.g. "00:00-00:06, 01:02-01:09". */
    public String findIntervals(String assetId, String topic) {
        Map<String, Object> body = post("/videos/{assetId}/intervals", Map.of("topic", topic), assetId);
        return body == null ? null : (String) body.get("data");
    }

    // --- Retrieval ---

    public boolean indexDocument(Long ownerId, String kind, Long refId, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Map<String, Object> body = post("/rag/documents", Map.of(
                "owner_id", ownerId, "kind", kind, "ref_id", refId, "text", text));
        return body != null && Boolean.TRUE.equals(body.get("embedded"));
    }

    public void deleteDocument(String kind, Long refId) {
        try {
            restClient.delete().uri("/rag/documents/{kind}/{refId}", kind, refId).retrieve().body(MAP);
        } catch (Exception e) {
            log.warn("AI service call failed (deleteDocument): {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<AiSearchHit> search(Long ownerId, String kind, String query, int limit) {
        Map<String, Object> body = post("/rag/search", Map.of(
                "owner_id", ownerId, "kind", kind, "query", query, "limit", limit));
        if (body == null) {
            return List.of();
        }
        List<Map<String, Object>> hits = (List<Map<String, Object>>) body.get("hits");
        if (hits == null) {
            return List.of();
        }
        return hits.stream()
                .map(hit -> new AiSearchHit(
                        ((Number) hit.get("ref_id")).longValue(),
                        (String) hit.get("text"),
                        ((Number) hit.get("score")).doubleValue()))
                .toList();
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP =
            new org.springframework.core.ParameterizedTypeReference<>() {};

    private Map<String, Object> post(String path, Map<String, Object> body, Object... uriVariables) {
        try {
            return restClient.post().uri(path, uriVariables).body(body).retrieve().body(MAP);
        } catch (Exception e) {
            log.warn("AI service call failed (" + path + "): {}", e.getMessage());
            return null;
        }
    }
}
