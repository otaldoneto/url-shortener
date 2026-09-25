package com.urlshortener.api;

import com.urlshortener.link.ShortLink;
import java.time.Instant;

public record ShortLinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt) {

    public static ShortLinkResponse from(ShortLink link, String baseUrl) {
        return new ShortLinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getTargetUrl(),
                link.getCreatedAt());
    }
}
