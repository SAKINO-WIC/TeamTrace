package com.teamtrace.backend.controller;

import com.teamtrace.backend.dto.common.ApiResponse;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.security.JwtTokenProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/v2")
public class CommonController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public CommonController(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @GetMapping("/users/names")
    public ApiResponse<Map<Long, String>> getUserNames(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam("ids") String idsParam) {
        requireLoggedIn(auth);
        List<Long> ids = Arrays.stream(idsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        Map<Long, String> names = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        u -> u.getId(),
                        u -> u.getName() != null ? u.getName() : ""));
        return ApiResponse.success(names);
    }

    private void requireLoggedIn(String auth) {
        String token = jwtTokenProvider.resolveBearerToken(auth);
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED);
        }
    }
}
