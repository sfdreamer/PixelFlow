package com.pixelflow.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import com.pixelflow.common.PixelFlowProperties;
import com.pixelflow.identity.mapper.IdentityMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonymous identity. The cookie carries a 256-bit random token; MySQL stores only its SHA-256.
 * Sessions are fixed-term (no sliding renewal) and cannot be recovered once the cookie is gone.
 */
@Service
public class SessionService {

    private final IdentityMapper identityMapper;
    private final PixelFlowProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SessionService(IdentityMapper identityMapper, PixelFlowProperties properties) {
        this.identityMapper = identityMapper;
        this.properties = properties;
    }

    public record SessionView(String userId, String displayName, Instant expiresAt) {
    }

    public record SessionResult(SessionView view, String issuedToken) {
    }

    @Transactional
    public SessionResult ensure(String rawToken) {
        Instant now = Instant.now();
        if (rawToken != null && !rawToken.isBlank()) {
            SessionIdentity existing = identityMapper.findIdentityByTokenHash(hash(rawToken), now);
            if (existing != null) {
                return new SessionResult(
                        new SessionView(existing.getUserId(), existing.getDisplayName(), existing.getExpiresAt()),
                        null);
            }
        }
        return createUserSession(now);
    }

    public AuthenticatedUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        SessionIdentity identity = identityMapper.findIdentityByTokenHash(hash(rawToken), Instant.now());
        return identity == null ? null : new AuthenticatedUser(identity.getUserId(), identity.getDisplayName());
    }

    /** Resolves the cookie token to a session, or null when missing/expired. */
    public SessionIdentity resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return identityMapper.findIdentityByTokenHash(hash(rawToken), Instant.now());
    }

    private SessionResult createUserSession(Instant now) {
        String userId = UUID.randomUUID().toString();
        String displayName = "游客-" + userId.substring(0, 4).toUpperCase(Locale.ROOT);

        AppUser user = new AppUser();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setCreatedAt(now);
        identityMapper.insertUser(user);

        String token = newToken();
        Instant expiresAt = now.plus(Duration.ofDays(properties.getSessionDays()));

        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTokenHash(hash(token));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(now);
        identityMapper.insertSession(session);

        return new SessionResult(new SessionView(userId, displayName, expiresAt), token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
