package com.teamtrace.backend.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WelcomeEmailSelectedRequest {

    @NotEmpty(message = "请选择要补发欢迎邮件的用户")
    @Size(max = 30, message = "单次最多选择30位用户")
    private List<Long> userIds;
}
