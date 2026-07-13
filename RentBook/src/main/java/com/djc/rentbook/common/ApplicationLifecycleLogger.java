package com.djc.rentbook.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ApplicationLifecycleLogger {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);
    private final Environment environment;

    public ApplicationLifecycleLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("RentBook started profiles={} port={} javaVersion={} userDir={}",
                Arrays.toString(environment.getActiveProfiles()),
                environment.getProperty("server.port", "8080"),
                System.getProperty("java.version"),
                System.getProperty("user.dir"));
    }

    @EventListener(ContextClosedEvent.class)
    public void onClosed() {
        log.info("RentBook stopped");
    }
}
