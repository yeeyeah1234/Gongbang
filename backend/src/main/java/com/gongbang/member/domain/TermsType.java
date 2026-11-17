package com.gongbang.member.domain;

/** terms_agreement.terms_type (AUTH-04 ①) */
public enum TermsType {
    SERVICE(true),
    PRIVACY(true),
    MARKETING(false);

    private final boolean required;

    TermsType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
