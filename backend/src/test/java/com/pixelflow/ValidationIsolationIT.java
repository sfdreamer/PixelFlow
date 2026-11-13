package com.pixelflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.pixelflow.support.AbstractMySqlIT;
import com.pixelflow.support.TestImages;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** B06: server-side upload/creation validation. B07: per-user isolation. */
class ValidationIsolationIT extends AbstractMySqlIT {

    @Test
    void b06_rejectsInvalidUploadsAndNeverPersistsThem() {
        String cookie = newSession();
        int assetsBefore = count("asset");

        ResponseEntity<String> unsupported = upload(cookie, TEMPLATE_ONE_IMAGE, "hello".getBytes(),
                "note.txt", "text/plain");
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(parse(unsupported).get("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        ResponseEntity<String> svg = upload(cookie, TEMPLATE_ONE_IMAGE,
                "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(), "x.png", "image/svg+xml");
        assertThat(svg.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        ResponseEntity<String> fake = upload(cookie, TEMPLATE_ONE_IMAGE, TestImages.fakePng(), "fake.png",
                "image/png");
        assertThat(fake.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(fake).get("code").asText()).isEqualTo("INVALID_IMAGE");

        byte[] big = new byte[5 * 1024 * 1024 + 10];
        System.arraycopy(TestImages.png(), 0, big, 0, Math.min(big.length, TestImages.png().length));
        ResponseEntity<String> tooLarge = upload(cookie, TEMPLATE_ONE_IMAGE, big, "big.png", "image/png");
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(parse(tooLarge).get("code").asText()).isEqualTo("FILE_TOO_LARGE");

        ResponseEntity<String> unknownTemplate = upload(cookie, "00000000-0000-4000-8000-000000000999",
                TestImages.png(), "x.png", "image/png");
        assertThat(unknownTemplate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(count("asset")).isEqualTo(assetsBefore);
    }

    @Test
    void b06_rejectsInvalidTaskRequests() {
        String cookie = newSession();
        String oneImageAsset = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");
        String twoImageAsset = uploadImage(cookie, TEMPLATE_TWO_IMAGES, "two-a.png");
        String secondTwoImageAsset = uploadImage(cookie, TEMPLATE_TWO_IMAGES, "two-b.png");
        int tasksBefore = count("task");

        // wrong count for a 2-image template
        ResponseEntity<String> mismatch = createTask(cookie, TEMPLATE_TWO_IMAGES, List.of(twoImageAsset), null);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(mismatch).get("code").asText()).isEqualTo("ASSET_COUNT_MISMATCH");

        // duplicate asset ids
        ResponseEntity<String> duplicated = createTask(cookie, TEMPLATE_TWO_IMAGES,
                List.of(twoImageAsset, twoImageAsset), null);
        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(duplicated).get("code").asText()).isEqualTo("ASSET_DUPLICATED");

        // asset uploaded for another template
        ResponseEntity<String> wrongTemplate = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(secondTwoImageAsset),
                null);
        assertThat(wrongTemplate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(wrongTemplate).get("code").asText()).isEqualTo("ASSET_TEMPLATE_MISMATCH");

        // unknown template
        ResponseEntity<String> unknownTemplate = createTask(cookie, "00000000-0000-4000-8000-000000000999",
                List.of(oneImageAsset), null);
        assertThat(unknownTemplate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(parse(unknownTemplate).get("code").asText()).isEqualTo("TEMPLATE_NOT_FOUND");

        // malformed ids are a 400, not a 404
        ResponseEntity<String> malformed = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of("not-a-uuid"), null);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // out-of-range simulation delay
        Map<String, Object> badDelay = new LinkedHashMap<>();
        badDelay.put("stageDelayMs", Map.of("GENERATE", 60_000));
        ResponseEntity<String> outOfRange = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(oneImageAsset), badDelay);
        assertThat(outOfRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(count("task")).isEqualTo(tasksBefore);
    }

    @Test
    void b07_isolatesAssetsTasksAndResultsBetweenAnonymousUsers() {
        String userA = newSession();
        String assetA = uploadImage(userA, TEMPLATE_ONE_IMAGE, "a.png");
        String taskA = parse(createTask(userA, TEMPLATE_ONE_IMAGE, List.of(assetA), null)).get("id").asText();
        JsonNode succeeded = awaitTerminal(userA, taskA, 20_000);
        assertThat(succeeded.get("status").asText()).isEqualTo("SUCCEEDED");

        String userB = newSession();

        ResponseEntity<String> assetB = rest.exchange("/api/v1/assets/" + assetA + "/content", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(userB)), String.class);
        assertThat(assetB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> taskB = rest.exchange("/api/v1/tasks/" + taskA, HttpMethod.GET,
                new HttpEntity<>(browserHeaders(userB)), String.class);
        assertThat(taskB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> resultB = rest.exchange("/api/v1/tasks/" + taskA + "/result", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(userB)), String.class);
        assertThat(resultB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> createWithOthersAsset = createTask(userB, TEMPLATE_ONE_IMAGE, List.of(assetA), null);
        assertThat(createWithOthersAsset.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(parse(createWithOthersAsset).get("code").asText()).isEqualTo("ASSET_NOT_FOUND");

        ResponseEntity<String> historyB = rest.exchange("/api/v1/tasks", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(userB)), String.class);
        assertThat(historyB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(historyB).get("items")).isEmpty();

        ResponseEntity<String> historyA = rest.exchange("/api/v1/tasks", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(userA)), String.class);
        assertThat(parse(historyA).get("items")).hasSize(1);
    }

    @Test
    void b07_unauthenticatedRequestsAre401EvenForPrivateMedia() {
        ResponseEntity<String> noCookie = rest.exchange("/api/v1/tasks", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(null)), String.class);
        assertThat(noCookie.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(parse(noCookie).get("code").asText()).isEqualTo("SESSION_REQUIRED");
    }

    @Test
    void unsafeRequestsFromAnotherOriginAreRejected() {
        String cookie = newSession();
        org.springframework.http.HttpHeaders headers = browserHeaders(cookie);
        headers.setOrigin("http://evil.example");
        ResponseEntity<String> response = rest.exchange("/api/v1/tasks", HttpMethod.POST,
                new HttpEntity<>(Map.of("templateId", TEMPLATE_ONE_IMAGE, "assetIds", List.of()), headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parse(response).get("code").asText()).isEqualTo("ORIGIN_NOT_ALLOWED");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
