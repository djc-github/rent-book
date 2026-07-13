package com.djc.rentbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RentBookApplication {

    private static final Logger log = LoggerFactory.getLogger(RentBookApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RentBookApplication.class, args);
        log.info("启动成功");
    }

}
