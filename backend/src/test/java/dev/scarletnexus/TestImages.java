package dev.scarletnexus;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Locally rebuilt PostgreSQL; the caller must verify its reviewed recipe before testing. */
final class TestImages {
  static final DockerImageName POSTGRES =
      DockerImageName.parse("scarlet-nexus-tools/postgres:17.12-gosu1.20-r1")
          .asCompatibleSubstituteFor("postgres");

  private TestImages() {}

  static PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>(POSTGRES).withImagePullPolicy(image -> false);
  }
}
