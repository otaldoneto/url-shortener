package com.urlshortener.api;

import com.urlshortener.link.ShortLinkService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final ShortLinkService service;

    public RedirectController(ShortLinkService service) {
        this.service = service;
    }

    // Only paths shaped like a code reach the database; anything else (favicon.ico, typos) is a plain 404.
    // 302 (temporary) instead of 301 (permanent): browsers cache a 301 and stop asking the server,
    // which would make every later visit invisible to the click counter.
    @GetMapping("/{code:[0-9A-Za-z]{7}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        URI target = URI.create(service.find(code).getTargetUrl());
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
