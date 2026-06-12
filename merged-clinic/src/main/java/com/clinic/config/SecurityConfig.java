package com.clinic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public OncePerRequestFilter sessionAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain) throws ServletException, IOException {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    HttpSession session = req.getSession(false);
                    if (session != null) {
                        String doctor = (String) session.getAttribute("loggedInDoctor");
                        String admin  = (String) session.getAttribute("loggedInAdmin");
                        if (doctor != null) {
                            SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(doctor, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
                        } else if (admin != null) {
                            SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(admin, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                        }
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: trang đặt lịch của bệnh nhân
                .requestMatchers("/", "/booking/**", "/css/**", "/js/**", "/images/**").permitAll()
                // Doctor routes
                .requestMatchers("/doctor/**").hasRole("DOCTOR")
                // Admin routes
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Auth routes
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: https://api.qrserver.com https://chart.googleapis.com; " +
                    "frame-ancestors 'none';"
                ))
                .referrerPolicy(r -> r.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .sessionManagement(s -> s.sessionFixation(f -> f.newSession()))
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .addFilterBefore(sessionAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
