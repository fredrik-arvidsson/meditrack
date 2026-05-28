package se.meditrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MediTrack — Läkemedelshantering för moderna vårdflöden.
 *
 * Entry point för Spring Boot-applikationen.
 */
@SpringBootApplication
public class MeditrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeditrackApplication.class, args);
    }
}