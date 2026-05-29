package se.meditrack.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Basklass för integrationstester. Startar en MySQL-container per
 * testkörning (samma image som produktion, mysql:8.4) och pekar
 * applikationen mot den via @DynamicPropertySource.
 *
 * Containern återanvänds mellan testklasser (static + reuse-flagga).
 * Flyway kör automatiskt vid app-start eftersom den finns på classpath
 * och vår application.yml redan har migrations-konfiguration.
 *
 * Tester som ärver från denna klass får:
 *  - En ren MySQL 8.4-instans med samma schema som produktion
 *  - Hela Spring-kontexten startad (@SpringBootTest)
 *  - Alla beans tillgängliga via @Autowired
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    // 'static' så containern delas mellan alla testmetoder i klassen.
    // Klassisk Testcontainers-pattern: starta en gång, kör många tester.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("meditrack_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    /**
     * Pekar Spring-konfigurationen mot containerns dynamiska URL/port.
     * Containern får en slumpmässig host-port (inte 3307) så den inte
     * krockar med produktions-containern vi har igång parallellt.
     */
    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }
}