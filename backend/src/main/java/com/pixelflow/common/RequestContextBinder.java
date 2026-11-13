package com.pixelflow.common;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.RequestContextFilter;

/**
 * Binds the current request to {@code RequestContextHolder} before the auth filter stores the
 * caller. Spring MVC would otherwise bind it only inside DispatcherServlet, which is too late for
 * filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestContextBinder extends RequestContextFilter {
}
