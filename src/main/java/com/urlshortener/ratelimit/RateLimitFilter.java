package com.urlshortener.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Runs before every controller. Shortening is rate-limited tighter than redirecting, since it writes
// to the database; anything else (actuator, unmatched paths) is left alone.
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int SHORTEN_CAPACITY = 10;
    private static final double SHORTEN_REFILL_PER_SECOND = 1;
    private static final int REDIRECT_CAPACITY = 60;
    private static final double REDIRECT_REFILL_PER_SECOND = 5;

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        String path = request.getRequestURI();
        boolean isShorten = HttpMethod.POST.matches(request.getMethod()) && path.equals("/api/links");
        boolean isRedirect = HttpMethod.GET.matches(request.getMethod()) && path.matches("/[0-9A-Za-z]{7}");

        if (isShorten && !limiter.tryConsume("shorten:" + ip, SHORTEN_CAPACITY, SHORTEN_REFILL_PER_SECOND)) {
            tooManyRequests(response);
            return;
        }
        if (isRedirect && !limiter.tryConsume("redirect:" + ip, REDIRECT_CAPACITY, REDIRECT_REFILL_PER_SECOND)) {
            tooManyRequests(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private static void tooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"Rate limit exceeded, try again shortly"}""");
    }
}
