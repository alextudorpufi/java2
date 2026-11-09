package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.util.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Collections;

/** Computes validators from the negotiated representation, including list responses. */
public class ApiEtagFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        if (!"GET".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        var wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        wrapper.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        if (wrapper.getStatus() == 200) {
            String tag = "W/\"" + DigestUtils.md5DigestAsHex(wrapper.getContentAsByteArray()) + "\"";
            wrapper.setHeader(HttpHeaders.ETAG, tag);
            ETag current = ETag.create(tag);
            boolean matches = Collections.list(request.getHeaders(HttpHeaders.IF_NONE_MATCH)).stream()
                    .flatMap(header -> ETag.parse(header).stream())
                    .anyMatch(candidate -> candidate.isWildcard() || candidate.compare(current, false));
            if (matches) {
                wrapper.resetBuffer();
                wrapper.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                wrapper.setHeader(HttpHeaders.CONTENT_LENGTH, null);
            }
        }
        wrapper.copyBodyToResponse();
    }
}
