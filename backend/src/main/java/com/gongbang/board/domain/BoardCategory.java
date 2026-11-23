package com.gongbang.board.domain;

/** BRD 커뮤니티 게시판 말머리 (DB 명세서 board_post.category). */
public enum BoardCategory {
    /** 공모전 이야기: 서비스 공모전 연결 필수 (BRD-002 ①) */
    CONTEST,
    /** 노하우 */
    TIP,
    /** 결과물 자랑: 결과물 링크·사진을 받는다 (BRD-002 ②, BRD-006) */
    SHOWCASE
}
