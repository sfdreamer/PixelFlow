package com.pixelflow.common;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the Origin header on unsafe methods under /api/v1.
 *
 * <p>Cookie SameSite=Strict and this check together block cross-site writes. There is no wildcard
 * credentialed CORS: a browser on another origin is rejected instead of being granted access.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String PREFIX = "/api/v1";

    private final PixelFlowProperties properties;
    private final ErrorResponses errorResponses;

    public OriginCheckFilter(PixelFlowProperties properties, ErrorResponses errorResponses) {
        this.properties = properties;
        this.errorResponses = errorResponses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !UNSAFE.contains(request.getMethod().toUpperCase(Locale.ROOT))
                || !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !isAllowed(normalize(origin))) {
            errorResponses.write(response, HttpStatus.FORBIDDEN, ErrorCodes.ORIGIN_NOT_ALLOWED,
                    "请求来源未被允许");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin) {
        return properties.getAllowedOrigins().stream()
                .map(OriginCheckFilter::normalize)
                .anyMatch(origin::equals);
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
