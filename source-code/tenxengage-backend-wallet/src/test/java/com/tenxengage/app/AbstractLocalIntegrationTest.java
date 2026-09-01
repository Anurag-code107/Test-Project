package com.tenxengage.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests that use the local PostgreSQL instance
 * (started via docker compose) instead of Testcontainers.
 *
 * Use this when Testcontainers cannot connect to Docker Desktop
 * (known issue with Docker Desktop 29.x and docker-java API versioning).
 *
 * Prerequisites: docker compose up -d (from tenxengage-backend/)
 */
@SpringBootTest
@ActiveProfiles("localtest")
public abstract class AbstractLocalIntegrationTest {
}
