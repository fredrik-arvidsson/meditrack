package se.meditrack.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal säkerhetskonfiguration. Just nu: HTTP Basic auth för enkelhet,
 * CSRF avstängt eftersom detta är ett stateless JSON-API (inte en
 * webbläsare som postar formulär — CSRF-skyddet är inte rätt försvar här).
 *
 * Stateless sessions: ingen JSESSIONID-cookie, varje request autentiseras
 * via Authorization-headern. Lämpligt för ett rent API som konsumeras av
 * en separat frontend.
 *
 * Riktig RBAC (rollbaserad åtkomst via @PreAuthorize på service-metoder
 * eller per endpoint) byggs ut i security-lagret. Just nu släpps alla
 * autentiserade requests igenom oavsett roll.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .httpBasic(basic -> {});
        return http.build();
    }
}