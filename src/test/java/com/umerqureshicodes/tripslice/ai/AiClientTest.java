package com.umerqureshicodes.tripslice.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// The Python service is replaced by a mock server, so nothing reaches TwelveLabs
class AiClientTest {

    private MockRestServiceServer server;
    private AiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai").defaultHeader("X-Service-Key", "k");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiClient(builder.build());
    }

    private void respond(HttpMethod httpMethod, String url, String json) {
        server.expect(requestTo(url)).andExpect(method(httpMethod)).andExpect(header("X-Service-Key", "k"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void callsEveryEndpoint() {
        respond(HttpMethod.POST, "http://ai/videos/index", "{\"video_id\":\"v1\"}");
        respond(HttpMethod.GET, "http://ai/videos/v1/asset", "{\"asset_id\":\"a1\"}");
        respond(HttpMethod.POST, "http://ai/videos/a1/summary", "{\"summary\":\"s\"}");
        server.expect(requestTo("http://ai/videos/a1/intervals")).andExpect(content().json("{\"topic\":\"beach\"}"))
                .andRespond(withSuccess("{\"data\":\"00:01-00:02\"}", MediaType.APPLICATION_JSON));
        respond(HttpMethod.DELETE, "http://ai/videos/v1", "{\"deleted\":true}");
        respond(HttpMethod.POST, "http://ai/rag/documents", "{\"embedded\":true}");
        respond(HttpMethod.DELETE, "http://ai/rag/documents/VIDEO/7", "{\"deleted\":1}");
        respond(HttpMethod.POST, "http://ai/rag/search", "{\"hits\":[{\"ref_id\":7,\"text\":\"t\",\"score\":0.5}]}");

        assertThat(client.indexVideo("https://s3/x")).isEqualTo("v1");
        assertThat(client.getAssetId("v1")).isEqualTo("a1");
        assertThat(client.summarize("a1")).isEqualTo("s");
        assertThat(client.findIntervals("a1", "beach")).isEqualTo("00:01-00:02");
        assertThat(client.deleteVideo("v1")).isTrue();
        assertThat(client.indexDocument(1L, AiClient.KIND_VIDEO, 7L, "text")).isTrue();
        client.deleteDocument(AiClient.KIND_VIDEO, 7L);
        assertThat(client.search(1L, AiClient.KIND_VIDEO, "q", 10)).containsExactly(new AiSearchHit(7L, "t", 0.5));
        server.verify();
    }

    @Test
    void degradesInsteadOfThrowingWhenTheServiceFails() {
        server.expect(ExpectedCount.manyTimes(), requestTo(org.hamcrest.Matchers.startsWith("http://ai/")))
                .andRespond(withServerError());
        assertThat(client.indexVideo("u")).isNull();
        assertThat(client.getAssetId("v")).isNull();
        assertThat(client.deleteVideo("v")).isFalse();
        assertThat(client.summarize("a")).isNull();
        assertThat(client.findIntervals("a", "t")).isNull();
        assertThat(client.indexDocument(1L, "VIDEO", 1L, "t")).isFalse();
        client.deleteDocument("VIDEO", 1L);
        assertThat(client.search(1L, "VIDEO", "q", 10)).isEmpty();
    }

    @Test
    void handlesEmptyAnswers() {
        respond(HttpMethod.POST, "http://ai/rag/search", "{}");
        assertThat(client.search(1L, "VIDEO", "q", 10)).isEmpty();
        // Blank text never calls out
        assertThat(client.indexDocument(1L, "VIDEO", 1L, " ")).isFalse();
        assertThat(client.indexDocument(1L, "VIDEO", 1L, null)).isFalse();
    }

    @Test
    void productionConstructorBuildsAClient() {
        // Port 1 refuses connections, so this fails fast and degrades to null
        assertThat(new AiClient("http://localhost:1", "key").getAssetId("x")).isNull();
    }
}
