package com.pixelflow.identity;

import java.time.Instant;

import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Request-scoped holder. Controllers and services read the caller from here; they never accept a
 * userId from the body or query string as an authorization source.
 */
@Component
public class CurrentUser {

    private static final String ATTRIBUTE = CurrentUser.class.getName();

    public record Context(AuthenticatedUser user, Instant sessionExpiresAt) {
    }

    public void set(AuthenticatedUser user, Instant sessionExpiresAt) {
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(ATTRIBUTE, new Context(user, sessionExpiresAt), RequestAttributes.SCOPE_REQUEST);
    }

    public void clear() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public Context requireContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof Context context) {
            return context;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.SESSION_REQUIRED, "会话已失效，请重新建立会话");
    }

    public AuthenticatedUser require() {
        return requireContext().user();
    }

    public String requireUserId() {
        return require().userId();
    }
}
