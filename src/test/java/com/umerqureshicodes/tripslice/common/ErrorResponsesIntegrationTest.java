package com.umerqureshicodes.tripslice.common;

import com.umerqureshicodes.tripslice.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Requests Spring rejects before our code runs, and unexpected failures, still reach the user as
// a readable { "error": "..." } instead of "Bad Request" or "Internal Server Error"
class ErrorResponsesIntegrationTest extends IntegrationTestBase {

    @Test
    void malformedJsonAndBadIdsGetAReadableMessage() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(post("/montages"), user).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The request couldn't be understood. Please refresh the page and try again."));
        mvc.perform(as(delete("/videos/abc"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The request couldn't be understood. Please refresh the page and try again."));
    }

    @Test
    void missingInputsSayWhatIsMissing() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(multipart("/videos"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please choose at least one video to upload."));
        mvc.perform(as(get("/search"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The request is missing \"q\"."));
    }

    @Test
    void unknownRoutesAndMethods() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(get("/nope"), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("That page or item doesn't exist."));
        mvc.perform(as(put("/videos"), user))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("That action isn't supported."));
    }

    @Test
    void unexpectedFailuresNeverLeakDetails() throws Exception {
        TestUser user = newUser();
        when(aiClient.search(anyLong(), eq("VIDEO"), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("connection pool exhausted at 10.0.0.5"));
        mvc.perform(as(get("/search").param("q", "beach"), user))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Something went wrong on our side. Please try again in a moment."));
    }
}
