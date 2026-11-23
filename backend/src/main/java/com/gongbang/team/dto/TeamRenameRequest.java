package com.gongbang.team.dto;

/** WS-08 ⑥⑦ 팀 이름 변경 요청. 1~20자 검사는 service가 한다 (어기면 400 TEAM_NAME_INVALID). */
public record TeamRenameRequest(String name) {}
