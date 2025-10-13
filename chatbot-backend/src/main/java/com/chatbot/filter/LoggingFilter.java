package com.chatbot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("\n📥 [Request] " + request.getMethod() + " " + request.getRequestURI());

        String authHeader = request.getHeader("Authorization");
        System.out.println("🔐 [Auth Header] " + (authHeader != null ? authHeader : "None"));

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.out.println("❌ [Exception] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;  // re-throw to let Spring handle the exception normally
        }

        System.out.println("📤 [Response] Status: " + response.getStatus());
    }
}
