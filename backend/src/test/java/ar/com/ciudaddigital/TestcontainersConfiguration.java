package ar.com.ciudaddigital;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Fijada a la misma versión que docker-compose.yml: los tests deben
		// correr contra el mismo motor que el entorno local.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
