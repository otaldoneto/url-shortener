package com.urlshortener.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {

    // With 3.5 trillion possible codes a collision is very unlikely; a few attempts are more than enough
    private static final int MAX_ATTEMPTS = 5;

    private final ShortLinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final Clock clock;

    public ShortLinkService(ShortLinkRepository repository, CodeGenerator codeGenerator, Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public ShortLink shorten(String url) {
        String targetUrl = validate(url);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            // The unique constraint in the database is the real guarantee; this check only avoids the error
            if (!repository.existsByCode(code)) {
                return repository.save(new ShortLink(code, targetUrl, clock.instant()));
            }
        }
        throw new IllegalStateException("Could not generate a unique code after " + MAX_ATTEMPTS + " attempts");
    }

    @Transactional(readOnly = true)
    public ShortLink find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    // Only absolute http(s) URLs with a host: anything else (javascript:, ftp:, relative paths) would make
    // the redirect unsafe or broken
    static String validate(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidUrlException("Only http and https URLs can be shortened");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("The URL must have a host");
        }
        return uri.toString();
    }
}
