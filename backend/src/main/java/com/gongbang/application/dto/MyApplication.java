package com.gongbang.application.dto;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;

/** 보는 사람 본인의 지원 (PST-005 myStatus·myApplicationId에 쓴다). */
public record MyApplication(Long applicationId, ApplicationStatus status) {

    public static MyApplication from(Application application) {
        return new MyApplication(application.getId(), application.getStatus());
    }
}
