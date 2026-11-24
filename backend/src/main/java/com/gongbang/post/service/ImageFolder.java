package com.gongbang.post.service;

/** 사진을 어느 폴더(키 앞부분)에 둘지: 모집글 공모전 사진(PST-007)과 커뮤니티 결과물 사진(BRD-006). */
public enum ImageFolder {
    POST("post"),
    BOARD("board");

    private final String path;

    ImageFolder(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
