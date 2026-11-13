package com.pixelflow.identity;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.ErrorResponses;
import com.pixelflow.common.PixelFlowProperties;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the pf_session cookie into a request-scoped caller and rejects private endpoints without
 * a valid session. Public reads (categories, templates, covers) and POST /session stay open.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final CurrentUser currentUser;
    private final PixelFlowProperties properties;
    private final ErrorResponses errorResponses;

    public SessionAuthFilter(SessionService sessionService, CurrentUser currentUser,
                             PixelFlowProperties properties, ErrorResponses errorResponses) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
        this.properties = properties;
        this.errorResponses = errorResponses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requiresAuth(request.getMethod(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Private payloads must never be cached by the Next.js proxy, a CDN or the browser.
        response.setHeader("Cache-Control", "no-store");

        SessionIdentity identity = sessionService.resolve(token(request));
        if (identity == null) {
            errorResponses.write(response, HttpStatus.UNAUTHORIZED, ErrorCodes.SESSION_REQUIRED,
                    "会话已失效，请重新建立会话");
            return;
        }
        currentUser.set(new AuthenticatedUser(identity.getUserId(), identity.getDisplayName()),
                identity.getExpiresAt());
        try {
            chain.doFilter(request, response);
        } finally {
            currentUser.clear();
        }
    }

    private String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean requiresAuth(String method, String uri) {
        if (!uri.startsWith("/api/v1")) {
            return false;
        }
        String verb = method.toUpperCase(Locale.ROOT);
        if (uri.equals("/api/v1/session")) {
            return !"POST".equals(verb);
        }
        if (!"GET".equals(verb) && !"HEAD".equals(verb)) {
            return true;
        }
        return !(uri.equals("/api/v1/categories") || uri.startsWith("/api/v1/templates"));
    }
}
