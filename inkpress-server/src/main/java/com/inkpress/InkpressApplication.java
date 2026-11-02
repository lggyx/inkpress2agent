package com.inkpress;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * InkPress 启动类
 * 个人博客与文章管理系统服务端
 */
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
@MapperScan("com.inkpress.mapper")
@SpringBootApplication
public class InkpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(InkpressApplication.class, args);
    }
}
