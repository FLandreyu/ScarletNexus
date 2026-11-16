package dev.scarletnexus;

import org.testcontainers.utility.DockerImageName;

/** Official immutable PostgreSQL index, shared by all disposable integration runtimes. */
final class TestImages {
  static final DockerImageName POSTGRES =
      DockerImageName.parse(
              "postgres:17.12-alpine3.24@sha256:a77aa70e641402a21411b92b721c58d10f23b50ae5fdd2333efd22d166e1e5f7")
          .asCompatibleSubstituteFor("postgres");

  private TestImages() {}
}
