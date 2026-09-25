package com.datingapp.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = com.bondcircle.BondCircleApplication.class)
@ActiveProfiles("test")
class ChatApplicationTests {

    @Test
    void contextLoads() {
    }
}
