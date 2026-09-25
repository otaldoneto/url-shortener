package com.urlshortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortenRequest(@NotBlank @Size(max = 2048) String url) {
}
