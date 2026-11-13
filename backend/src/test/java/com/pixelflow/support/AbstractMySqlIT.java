package com.pixelflow.support;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelflow.execution.WorkerControl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-test base that talks HTTP through the real Spring stack and asserts against real
 * MySQL. The database comes from {@code PF_TEST_MYSQL_URL} (default: a local isolated instance on
 * port 3307); every test runs against the dedicated {@code pixelflow_it} schema.
 *
 * <p>All integration tests share that schema while Spring keeps one context (and therefore one
 * scheduler thread) alive per distinct context configuration. To keep fixtures deterministic the
 * worker gate is closed around every test: it only runs between {@link #resumeScheduler()} and the
 * {@code @AfterEach} cleanup, which restores the parked state for the next context that reuses it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractMySqlIT {

    protected static final String ORIGIN = "http://localhost:3000";
    protected static final String TEMPLATE_ONE_IMAGE = "00000000-0000-4000-8000-000000000101";
    protected static final String TEMPLATE_TWO_IMAGES = "00000000-0000-4000-8000-000000000201";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected WorkerControl workerControl;

    @BeforeEach
    void cleanRuntimeTables() {
        workerControl.pause();
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : List.of("task_result", "task_step", "task_asset", "task", "asset",
                "user_session", "app_user")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        if (schedulerNeeded()) {
            resumeScheduler();
        }
    }

    @AfterEach
    void parkScheduler() {
        workerControl.pause();
    }

    /** Tests that assert on a parked queue and drive claim/execute themselves override this. */
    protected boolean schedulerNeeded() {
        return true;
    }

    /** Lets the background worker advance queued tasks for the current test. */
    protected void resumeScheduler() {
        workerControl.resume();
    }

    protected HttpHeaders browserHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    protected String newSession() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        ResponseEntity<String> response = rest.exchange("/api/v1/session", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("Set-Cookie for a new anonymous identity").isNotNull();
        return setCookie.split(";")[0];
    }

    protected JsonNode parse(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (IOException e) {
            throw new IllegalStateException("response is not JSON: " + response.getBody(), e);
        }
    }

    protected ResponseEntity<String> upload(String cookie, String templateId, byte[] bytes, String fileName,
                                            String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, cookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("templateId", templateId);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(resource, partHeaders));

        return rest.exchange("/api/v1/assets", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected String uploadImage(String cookie, String templateId, String fileName) {
        ResponseEntity<String> response = upload(cookie, templateId, TestImages.png(), fileName, "image/png");
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return parse(response).get("id").asText();
    }

    protected ResponseEntity<String> createTask(String cookie, String templateId, List<String> assetIds,
                                                Map<String, Object> simulation) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("templateId", templateId);
        body.put("assetIds", assetIds);
        if (simulation != null) {
            body.put("simulation", simulation);
        }
        return rest.exchange("/api/v1/tasks", HttpMethod.POST,
                new HttpEntity<>(body, browserHeaders(cookie)), String.class);
    }

    protected JsonNode getTask(String cookie, String taskId) {
        ResponseEntity<String> response = rest.exchange("/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(browserHeaders(cookie)), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return parse(response);
    }

    protected JsonNode awaitTerminal(String cookie, String taskId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode task = getTask(cookie, taskId);
        while (System.currentTimeMillis() < deadline) {
            String status = task.get("status").asText();
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return task;
            }
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            task = getTask(cookie, taskId);
        }
        throw new AssertionError("task " + taskId + " did not reach a terminal state in " + timeoutMs + "ms");
    }
}
