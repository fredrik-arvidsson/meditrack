package se.meditrack.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Minimal säkerhetskonfiguration för utvecklingsläge.
 *
 * Auth: i nuvarande iteration släpps alla requests igenom utan
 * autentisering. Detta är medvetet förenklat - en riktig produktion
 * skulle ha rollbaserad åtkomst (NURSE/PHARMACIST/ADMIN) via
 * @PreAuthorize eller endpoint-matchers. Se MEDITRACK_AFFARSREGLER.md
 * avsnitt om rollmatrisen för planerad design.
 *
 * CSRF avstängt eftersom detta är ett stateless JSON-API som
 * konsumeras av en separat React-frontend. CSRF-skyddet är designat
 * för formulär-baserade webbappar med sessions; det passar inte här.
 *
 * Stateless sessions: ingen JSESSIONID-cookie, varje request står
 * på egen hand. Frontend håller ingen sessionsstate på serversidan.
 *
 * CORS: tillåter localhost:5173 (Vite dev-server) att anropa
 * backend. För produktion skulle deploy-URL:en läggas till - eller
 * helst skulle frontend och backend serveras från samma origin via
 * en reverse proxy, så CORS inte behövs alls.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}