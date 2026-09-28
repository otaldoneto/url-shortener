package com.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
@Import({TestcontainersConfiguration.class, RateLimitFlowTest.FixedClockConfig.class})
class RateLimitFlowTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    StringRedisTemplate redis;

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
