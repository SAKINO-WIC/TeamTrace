package com.teamtrace.backend.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailValidationTest {

    @Test
    void acceptsCommonChineseMailDomains() {
        assertTrue(EmailValidation.isAllowedEmail("a@qq.com"));
        assertTrue(EmailValidation.isAllowedEmail("b@163.com"));
        assertTrue(EmailValidation.isAllowedEmail("c@126.com"));
        assertTrue(EmailValidation.isAllowedEmail("d@foxmail.com"));
        assertTrue(EmailValidation.isAllowedEmail("e@sina.com"));
        assertTrue(EmailValidation.isAllowedEmail("f@139.com"));
        assertTrue(EmailValidation.isAllowedEmail("g@gmail.com"));
        assertTrue(EmailValidation.isAllowedEmail("h@stu.pku.edu.cn"));
    }

    @Test
    void rejectsUnsupportedDomains() {
        assertFalse(EmailValidation.isAllowedEmail("a@yahoo.com"));
        assertFalse(EmailValidation.isAllowedEmail("b@proton.me"));
        assertFalse(EmailValidation.isAllowedEmail("not-an-email"));
    }

    @Test
    void normalizesCase() {
        assertTrue(EmailValidation.isAllowedEmail("User@163.COM"));
    }
}
