package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// End to end: the filter actually blocks requests once the bucket for an IP runs dry
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitFlowTest {

    @Autowired
    MockMvcTester mvc;
    @Autowired
    StringRedisTemplate redis;

    // Spring reaproveita o mesmo contexto (e o mesmo Redis) entre classes de teste com a mesma
    // configuração, então um bucket de outra classe pode vazar para esta se não for limpo antes.
    @BeforeEach
    void cleanRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void blocksShorteningAfterTheBucketIsEmpty() {
        for (int i = 0; i < 10; i++) {
            assertThat(shorten("https://example.com/" + i)).hasStatus(201);
        }

        assertThat(shorten("https://example.com/one-too-many")).hasStatus(429)
                .hasHeader("Retry-After", "1");
    }

    @Test
    void blocksRedirectsAfterTheBucketIsEmpty() throws Exception {
        String code = com.jayway.jsonpath.JsonPath.read(
                shorten("https://example.com/redirect-target").getResponse().getContentAsString(), "$.code");

        for (int i = 0; i < 60; i++) {
            assertThat(mvc.get().uri("/" + code)).hasStatus(302);
        }

        assertThat(mvc.get().uri("/" + code)).hasStatus(429);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult shorten(String url) {
        return mvc.post().uri("/api/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}").exchange();
    }
}
