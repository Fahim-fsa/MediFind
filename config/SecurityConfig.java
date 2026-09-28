package com.medifind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: landing page, every auth screen, static assets, health check.
                        .requestMatchers("/", "/auth/**", "/css/**", "/js/**", "/images/**",
                                "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Role-gated areas — both the page routes and their JSON API counterparts.
                        .requestMatchers("/patient/**", "/api/patient/**").hasRole("PATIENT")
                        .requestMatchers("/pharmacist/**", "/api/pharmacist/**").hasRole("PHARMACIST")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                // We manage the session ourselves in AuthService (see establishSecurityContext),
                // so we only need Spring Security to clear it correctly on logout.
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/?loggedout=true")
                        .permitAll()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String target = request.getRequestURI().startsWith("/admin")
                                    ? "/auth/admin-login"
                                    : "/auth/login";
                            response.sendRedirect(request.getContextPath() + target);
                        })

                );

        return http.build();
    }
}
