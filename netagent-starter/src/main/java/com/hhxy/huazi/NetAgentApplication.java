package com.hhxy.huazi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = {
        "com.hhxy.huazi.user.mapper",
        "com.hhxy.huazi.agent.mapper"
})
public class NetAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetAgentApplication.class, args);
    }
}
