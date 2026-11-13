package com.pixelflow.identity;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.pixelflow.common.PixelFlowProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUser currentUser;
    private final PixelFlowProperties properties;

    public SessionController(SessionService sessionService, CurrentUser currentUser,
                             PixelFlowProperties properties) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    /** Creates an anonymous identity when the cookie is missing/expired, otherwise returns it. */
    @PostMapping
    public ResponseEntity<SessionService.SessionView> ensure(HttpServletRequest request) {
        SessionService.SessionResult result = sessionService.ensure(readToken(request));
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.issuedToken() != null) {
            ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), result.issuedToken())
                    .httpOnly(true)
                    .secure(properties.isCookieSecure())
                    .sameSite("Strict")
                    .path("/api")
                    .maxAge(Duration.ofDays(properties.getSessionDays()))
                    .build();
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return builder.body(result.view());
    }

    @GetMapping
    public ResponseEntity<SessionService.SessionView> current() {
        CurrentUser.Context context = currentUser.requireContext();
        SessionService.SessionView view = new SessionService.SessionView(
                context.user().userId(), context.user().displayName(), context.sessionExpiresAt());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(view);
    }

    private String readToken(HttpServletRequest request) {
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
}
