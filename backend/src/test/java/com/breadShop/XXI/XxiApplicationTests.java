package com.breadShop.XXI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test — ต้องการ MySQL จริง
 * รันด้วย: mvnw test -Dintegration=true
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "integration", matches = "true")
class XxiApplicationTests {

	@Test
	void contextLoads() {
	}

}
