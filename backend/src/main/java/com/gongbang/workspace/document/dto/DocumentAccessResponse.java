package com.gongbang.workspace.document.dto;

/** DOC-08 동기화 서버용: 문서를 열려는 회원이 그 문서 팀의 현재 팀원일 때 돌려준다. leader = 팀장 여부. */
public record DocumentAccessResponse(Long memberId, String nickname, Long teamId, boolean leader) {}
