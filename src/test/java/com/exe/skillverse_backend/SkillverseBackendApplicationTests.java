package com.exe.skillverse_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.exe.skillverse_backend.portfolio_service.service.CVGeneratorAIService;

@SpringBootTest
class SkillverseBackendApplicationTests {

    @MockBean
    private CVGeneratorAIService cvGeneratorAIService;

    @Test
    void contextLoads() {
    }

}
