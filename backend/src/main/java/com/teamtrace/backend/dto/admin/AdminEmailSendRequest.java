package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminEmailSendRequest {

    @NotBlank(message = "收件范围不能为空")
    private String recipientScope;

    @Size(max = 100, message = "手动邮箱单次最多100个")
    private List<String> manualEmails;

    @NotBlank(message = "邮件标题不能为空")
    @Size(max = 120, message = "邮件标题不能超过120个字符")
    private String subject;

    @NotBlank(message = "邮件正文不能为空")
    @Size(max = 5000, message = "邮件正文不能超过5000个字符")
    private String body;
}
