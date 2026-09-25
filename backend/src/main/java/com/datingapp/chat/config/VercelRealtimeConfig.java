package com.datingapp.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("vercel")
@EnableScheduling
public class VercelRealtimeConfig {}
