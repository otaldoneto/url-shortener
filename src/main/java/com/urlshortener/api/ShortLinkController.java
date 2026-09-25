package com.urlshortener.api;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class ShortLinkController {

    private final ShortLinkService service;
    private final String baseUrl;

    public ShortLinkController(ShortLinkService service, @Value("${app.base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping
    public ResponseEntity<ShortLinkResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortLink link = service.shorten(request.url());
        ShortLinkResponse response = ShortLinkResponse.from(link, baseUrl);
        return ResponseEntity.created(URI.create("/api/links/" + link.getCode())).body(response);
    }

    @GetMapping("/{code}")
    public ShortLinkResponse get(@PathVariable String code) {
        return ShortLinkResponse.from(service.find(code), baseUrl);
    }
}
