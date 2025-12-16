package com.exe.skillverse_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.exe.skillverse_backend.portfolio_service.service.impl.CVGeneratorAIServiceImpl;

@SpringBootTest
class SkillverseBackendApplicationTests {

    @MockBean
    private CVGeneratorAIServiceImpl cvGeneratorAIService;

    @Test
    void contextLoads() {
    }

}
