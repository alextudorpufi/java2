package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;

/** Legacy routes remain aliases while clients move to the explicit v1 contract. */
public class LegacyApiFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.matches("/api/(customers|vehicles|deliveries|scenarios)(/.*)?")) {
            String successor = ServletUriComponentsBuilder.fromCurrentRequest()
                    .replacePath(request.getContextPath() + "/api/v1/" + path.substring("/api/".length()))
                    .build().toUriString();
            response.addHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
        }
        chain.doFilter(request, response);
    }
}
