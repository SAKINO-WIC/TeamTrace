package com.teamtrace.backend.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailValidation {

    /**
     * 注册/登录/验证码允许的邮箱域名（不区分大小写）。
     * 腾讯：qq、foxmail；网易：163、126、yeah、188；新浪；搜狐；139；常见国际邮箱；*.edu.cn
     */
    public static final String ALLOWED_EMAIL_REGEXP =
            "^[\\w.+-]+@"
                    + "(qq\\.com|foxmail\\.com|"
                    + "163\\.com|126\\.com|yeah\\.net|188\\.com|"
                    + "sina\\.com|sina\\.cn|"
                    + "sohu\\.com|"
                    + "139\\.com|"
                    + "gmail\\.com|outlook\\.com|hotmail\\.com|live\\.cn|live\\.com|icloud\\.com|"
                    + "([\\w-]+\\.)+edu\\.cn"
                    + ")$";

    public static final Pattern ALLOWED_EMAIL =
            Pattern.compile(ALLOWED_EMAIL_REGEXP, Pattern.CASE_INSENSITIVE);

    /** Bean Validation @Pattern(regexp = ...) 与前端提示共用 */
    public static final String ALLOWED_EMAIL_MESSAGE =
            "请使用支持的邮箱：QQ/Foxmail、网易(163/126)、新浪、搜狐、139、Gmail/Outlook 或学校 edu.cn 邮箱";

    public static final String ALLOWED_EMAIL_HINT =
            "QQ、163、126、Foxmail、新浪、搜狐、139、Gmail、Outlook 或 *.edu.cn";

    /** @deprecated 使用 {@link #ALLOWED_EMAIL} */
    public static final Pattern QQ_EMAIL = ALLOWED_EMAIL;

    private EmailValidation() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedEmail(String email) {
        return ALLOWED_EMAIL.matcher(normalize(email)).matches();
    }

    /** @deprecated 使用 {@link #isAllowedEmail} */
    public static boolean isQqEmail(String email) {
        return isAllowedEmail(email);
    }

    public static boolean isLegacyPhoneLogin(String loginId) {
        return loginId != null && loginId.trim().matches("^\\d{11}$");
    }
}
