package dev.scarletnexus;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@OpenAPIDefinition(
    info =
        @Info(
            title = "Scarlet Nexus API",
            version = "0.1.0",
            description = "P0 implemented endpoints only"))
public class ScarletNexusApplication {
  public static void main(String[] args) {
    SpringApplication.run(ScarletNexusApplication.class, args);
  }
}
