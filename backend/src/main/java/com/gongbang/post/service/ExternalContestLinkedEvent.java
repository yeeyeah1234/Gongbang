package com.gongbang.post.service;

/** PST-002 ⑤: 외부 공모전 링크를 넣은 모집글이 등록됐다. 커밋 뒤 링크의 대표 이미지를 불러온다. */
public record ExternalContestLinkedEvent(Long postId, String url) {}
