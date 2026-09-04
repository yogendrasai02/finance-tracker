package com.financetracker;

import com.financetracker.db.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// @ServiceConnection is not used here: it would connect everything as the container's admin account, defeating the ft_app/ft_migrator split this test is meant to prove (DM-28).
@SpringBootTest
class BackendApplicationTests {

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
		registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
		registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
		registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
		registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
		registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
	}

	@Test
	void contextLoads() {
	}

}
