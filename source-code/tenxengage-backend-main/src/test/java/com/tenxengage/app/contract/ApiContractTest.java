package com.tenxengage.app.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase R: API Contract Validation
 * Verifies that contract YAML files exist for all major API resources
 * and contain required endpoint definitions.
 */
class ApiContractTest {

    private static final Path CONTRACTS_DIR = Paths.get("../tenxengage-contracts/endpoints");

    @Test
    void contractsDirectoryExists() {
        assertThat(Files.isDirectory(CONTRACTS_DIR))
                .as("Contracts directory should exist at %s", CONTRACTS_DIR)
                .isTrue();
    }

    @Test
    void allMajorResourcesHaveContracts() {
        List<String> requiredContracts = List.of(
                "auth.yaml",
                "users.yaml",
                "incentives.yaml",
                "claims.yaml",
                "clients.yaml",
                "connectors.yaml",
                "compliance.yaml",
                "partner-companies.yaml",
                "feature-flags.yaml",
                "onboarding.yaml",
                "financial-compliance.yaml",
                "kyc.yaml"
        );

        for (String contract : requiredContracts) {
            Path contractFile = CONTRACTS_DIR.resolve(contract);
            assertThat(Files.exists(contractFile))
                    .as("Contract file %s should exist", contract)
                    .isTrue();
        }
    }

    @Test
    void contractFilesAreNotEmpty() throws IOException {
        try (Stream<Path> files = Files.list(CONTRACTS_DIR)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                    .forEach(file -> {
                        try {
                            long size = Files.size(file);
                            assertThat(size)
                                    .as("Contract file %s should not be empty", file.getFileName())
                                    .isGreaterThan(0);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + file, e);
                        }
                    });
        }
    }

    @Test
    void authContractDefinesLoginEndpoint() throws IOException {
        Path authContract = CONTRACTS_DIR.resolve("auth.yaml");
        String content = Files.readString(authContract);

        assertThat(content).contains("/api/v1/auth/login");
        assertThat(content).contains("post:");
        assertThat(content).contains("email");
        assertThat(content).contains("password");
    }

    @Test
    void incentivesContractDefinesCrudEndpoints() throws IOException {
        Path incentiveContract = CONTRACTS_DIR.resolve("incentives.yaml");
        String content = Files.readString(incentiveContract);

        assertThat(content).contains("/api/v1/incentives");
        assertThat(content).contains("get:");
        assertThat(content).contains("post:");
    }

    @Test
    void usersContractDefinesCrudEndpoints() throws IOException {
        Path usersContract = CONTRACTS_DIR.resolve("users.yaml");
        String content = Files.readString(usersContract);

        assertThat(content).contains("/api/v1/users");
        assertThat(content).contains("get:");
        assertThat(content).contains("post:");
    }
}
