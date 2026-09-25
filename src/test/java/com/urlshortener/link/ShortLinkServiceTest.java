package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private final ShortLinkRepository repository = mock(ShortLinkRepository.class);
    private final CodeGenerator codeGenerator = mock(CodeGenerator.class);
    private final LinkCache cache = mock(LinkCache.class);
    private final ShortLinkService service = new ShortLinkService(repository, codeGenerator, cache,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void saveReturnsTheEntity() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shortensAValidUrl() {
        when(codeGenerator.generate()).thenReturn("aB3xK9z");

        ShortLink link = service.shorten("https://example.com/some/long/path?q=1");

        assertThat(link.getCode()).isEqualTo("aB3xK9z");
        assertThat(link.getTargetUrl()).isEqualTo("https://example.com/some/long/path?q=1");
        assertThat(link.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void triesAnotherCodeWhenTheFirstIsTaken() {
        when(codeGenerator.generate()).thenReturn("taken01", "free002");
        when(repository.existsByCode("taken01")).thenReturn(true);

        ShortLink link = service.shorten("https://example.com");

        assertThat(link.getCode()).isEqualTo("free002");
    }

    @Test
    void givesUpAfterFiveCollisions() {
        when(codeGenerator.generate()).thenReturn("taken01");
        when(repository.existsByCode("taken01")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten("https://example.com")).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }


    @Test
    void redirectUsesTheCacheWhenAvailable() {
        when(cache.getTargetUrl("abc1234")).thenReturn(Optional.of("https://cached.example.com"));

        String url = service.redirectTo("abc1234");

        assertThat(url).isEqualTo("https://cached.example.com");
        verify(repository, never()).findByCode(any());
    }

    @Test
    void redirectFallsBackToTheDatabaseOnACacheMissAndFillsTheCache() {
        when(cache.getTargetUrl("abc1234")).thenReturn(Optional.empty());
        when(repository.findByCode("abc1234"))
                .thenReturn(java.util.Optional.of(new ShortLink("abc1234", "https://db.example.com", NOW)));

        String url = service.redirectTo("abc1234");

        assertThat(url).isEqualTo("https://db.example.com");
        verify(cache).cacheTargetUrl("abc1234", "https://db.example.com");
    }

    @Test
    void redirectCountsAClickOnACacheHit() {
        when(cache.getTargetUrl("abc1234")).thenReturn(Optional.of("https://cached.example.com"));

        service.redirectTo("abc1234");

        verify(cache).incrementClicks("abc1234");
    }

    @Test
    void redirectToAnUnknownCodeDoesNotTouchTheCache() {
        when(cache.getTargetUrl("abc1234")).thenReturn(Optional.empty());
        when(repository.findByCode("abc1234")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.redirectTo("abc1234")).isInstanceOf(LinkNotFoundException.class);
        verify(cache, never()).cacheTargetUrl(any(), any());
        verify(cache, never()).incrementClicks(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "HTTPS://Example.com/path", "  https://example.com  "})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(ShortLinkService.validate(url)).isEqualTo(url.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://example.com/file", "example.com", "/relative/path",
            "https://", "http://exa mple.com"})
    void rejectsUrlsThatAreNotAbsoluteHttp(String url) {
        assertThatThrownBy(() -> ShortLinkService.validate(url)).isInstanceOf(InvalidUrlException.class);
    }
}
