package com.gongbang.global.config;

import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MY-01 프로필 사진: app.upload-dir 폴더를 /uploads/** 주소로 보여 준다 (정진우 추가) */
@Configuration
public class UploadConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/"; // 폴더가 아직 없으면 끝에 / 가 안 붙는다
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
