package com.bondcircle.security;

import com.bondcircle.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null && jwtService.validateToken(jwt)) {
                String subject = null;
                try {
                    subject = jwtService.extractEmail(jwt);
                } catch (Exception ignored) {}

                com.bondcircle.entity.User user = null;
                if (subject != null && subject.contains("@")) {
                    user = userRepository.findByEmail(subject).orElse(null);
                }

                Long userId = null;
                try {
                    userId = jwtService.extractUserId(jwt);
                } catch (Exception ignored) {}

                if (user == null && userId != null) {
                    user = userRepository.findById(userId).orElse(null);
                }

                Object principal = user;
                if (principal == null && userId != null) {
                    principal = com.datingapp.chat.security.UserPrincipal.create(userId, jwtService.extractRoles(jwt));
                }

                if (principal != null) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal instanceof com.datingapp.chat.security.UserPrincipal up ? up.getAuthorities() : Collections.emptyList()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ignored) {
            // Invalid token, context remains unauthenticated
        }

        filterChain.doFilter(request, response);
    }
}
