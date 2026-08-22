package com.jobseekercopilot.systemdata;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SystemDataProperties.class)
public class SystemDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemDataServiceApplication.class, args);
    }
}
