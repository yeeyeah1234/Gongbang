package com.gongbang.workspace;

import java.util.Collection;

/**
 * 문서(DOC-02)·화이트보드(WB-01) 생성 시 기본 제목 번호를 계산한다.
 *
 * <p>같은 팀의 삭제되지 않은 항목 중 「기준 이름」을 1, 「기준 이름 (N)」(N은 양의 정수)을 N으로 보고 가장 큰 번호 + 1을 돌려준다. 해당하는 제목이 없으면
 * 기준 이름을 그대로 돌려준다. 사용자가 바꾼 제목이나 「(복사본)」「(abc)」「(0)」 같은 형태는 무시한다.
 */
public final class DefaultTitleGenerator {

    private DefaultTitleGenerator() {}

    public static String nextTitle(String baseTitle, Collection<String> existingTitles) {
        if (existingTitles == null || existingTitles.isEmpty()) {
            return baseTitle;
        }
        String prefix = baseTitle + " (";
        long maxNumber = 0;

        for (String title : existingTitles) {
            if (title == null) {
                continue;
            }
            if (title.equals(baseTitle)) {
                maxNumber = Math.max(maxNumber, 1);
                continue;
            }
            if (title.startsWith(prefix) && title.endsWith(")")) {
                String numberPart = title.substring(prefix.length(), title.length() - 1);
                long parsed = parsePositiveNumber(numberPart);
                if (parsed > maxNumber) {
                    maxNumber = parsed;
                }
            }
        }

        return maxNumber == 0 ? baseTitle : baseTitle + " (" + (maxNumber + 1) + ")";
    }

    private static long parsePositiveNumber(String text) {
        if (text.isEmpty() || text.charAt(0) == '0') {
            return 0;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                return 0;
            }
        }
        try {
            long value = Long.parseLong(text);
            return value == Long.MAX_VALUE ? 0 : value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
