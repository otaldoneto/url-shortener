package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// End to end with a real PostgreSQL: shorten a URL, then follow the short link
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShortLinkFlowTest {

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult shorten(String json) {
        return mvc.post().uri("/api/links").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void shortensAUrlAndRedirectsToIt() throws Exception {
        MvcTestResult created = shorten("""
                {"url":"https://example.com/some/long/path?q=1"}
                """);
        assertThat(created).hasStatus(201);
        String code = JsonPath.read(created.getResponse().getContentAsString(), "$.code");
        assertThat(created).hasHeader("Location", "/api/links/" + code);
        assertThat(created).bodyJson().extractingPath("$.shortUrl").isEqualTo("http://localhost:8080/" + code);

        assertThat(mvc.get().uri("/" + code)).hasStatus(302)
                .hasHeader("Location", "https://example.com/some/long/path?q=1");

        assertThat(mvc.get().uri("/api/links/" + code)).hasStatus(200)
                .bodyJson()
                .extractingPath("$.targetUrl")
                .isEqualTo("https://example.com/some/long/path?q=1");
    }

    @Test
    void unknownCodeIsNotFound() {
        assertThat(mvc.get().uri("/zzzzzzz")).hasStatus(404);
        assertThat(mvc.get().uri("/api/links/zzzzzzz")).hasStatus(404);
    }

    @Test
    void pathsThatCannotBeACodeAreNotFound() {
        assertThat(mvc.get().uri("/favicon.ico")).hasStatus(404);
        assertThat(mvc.get().uri("/abc")).hasStatus(404);
    }

    @Test
    void rejectsInvalidUrls() {
        assertThat(shorten("""
                {"url":"javascript:alert(1)"}
                """)).hasStatus(400).bodyJson().extractingPath("$.detail")
                .isEqualTo("Only http and https URLs can be shortened");
        assertThat(shorten("""
                {"url":"  "}
                """)).hasStatus(400);
        assertThat(shorten("{}")).hasStatus(400);
    }

    @Test
    void rejectsUrlsLongerThanTheColumn() {
        String tooLong = "https://example.com/" + "a".repeat(2048);

        assertThat(shorten("{\"url\":\"" + tooLong + "\"}")).hasStatus(400);
    }
}
