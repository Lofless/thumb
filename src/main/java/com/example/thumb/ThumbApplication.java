package com.example.thumb;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.thumb.src.mapper")
public class ThumbApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbApplication.class, args);
    }

}
