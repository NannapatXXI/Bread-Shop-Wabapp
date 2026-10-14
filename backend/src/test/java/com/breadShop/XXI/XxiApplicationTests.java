package com.breadShop.XXI;

import com.breadShop.XXI.service.Mailservice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "integration", matches = "true")
class XxiApplicationTests {

    @MockitoBean
    Mailservice mailService;

    @Test
    void contextLoads() {
    }

}
