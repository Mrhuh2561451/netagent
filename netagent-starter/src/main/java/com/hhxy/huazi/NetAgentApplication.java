package com.hhxy.huazi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hhxy.huazi.user.mapper")
public class NetAgentApplication {


    public static void main(String[] args) {
        SpringApplication.run(NetAgentApplication.class, args);
    }
}
