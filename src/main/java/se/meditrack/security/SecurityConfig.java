package se.meditrack.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Säkerhetskonfiguration: HTTP Basic auth + rollbaserad åtkomst.
 *
 * Auth-modell: HTTP Basic. Varje request bär användarnamn + lösenord i
 * Authorization-headern. Valt medvetet för att hålla API:t STATELESS
 * (ingen session, ingen cookie) — konsekvent med resten av designen.
 * I produktion vore JWT eller OIDC lämpligare (riktig token-hantering,
 * utloggning), men för att demonstrera rollbaserad auktorisering håller
 * Basic auth arkitekturen ren.
 *
 * @EnableMethodSecurity slår på @PreAuthorize, så att enskilda endpoints
 * kan kräva en viss roll (separation of duties — se OrderController).
 *
 * CSRF avstängt: stateless JSON-API utan session, CSRF-skyddet (designat
 * för sessionsbaserade formulär-appar) är inte tillämpligt.
 *
 * CORS: tillåter Vite dev-servern (localhost:5173) att anropa backend.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight (OPTIONS) måste släppas igenom oautentiserat.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Allt övrigt kräver inloggning. Finkornig rollkontroll
                        // sker per endpoint med @PreAuthorize.
                        .anyRequest().authenticated())
                .httpBasic(basic -> {});
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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