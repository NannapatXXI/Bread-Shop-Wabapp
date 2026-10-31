package com.breadShop.XXI;

import com.breadShop.XXI.integration.MySqlContainerConfig;
import com.breadShop.XXI.service.Mailservice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
class XxiApplicationTests {

    @MockitoBean
    Mailservice mailService;

    @Test
    void contextLoads() {
    }

}
