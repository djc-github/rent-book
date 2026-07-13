package com.djc.rentbook.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String TRACE_ID = "traceId";
    private static final long SLOW_REQUEST_MILLIS = 1_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = traceId(request);
        long startedAt = System.currentTimeMillis();
        MDC.put(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            log.info("HTTP request started method={} uri={} query={} remote={}",
                    request.getMethod(), request.getRequestURI(), safe(request.getQueryString()), request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startedAt;
            if (duration >= SLOW_REQUEST_MILLIS) {
                log.warn("HTTP request completed slowly method={} uri={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
            } else {
                log.info("HTTP request completed method={} uri={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
            }
            MDC.remove(TRACE_ID);
        }
    }

    private String traceId(HttpServletRequest request) {
        String header = request.getHeader("X-Trace-Id");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
