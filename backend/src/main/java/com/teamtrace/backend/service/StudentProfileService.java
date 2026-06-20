package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.student.StudentDeleteAccountRequest;
import com.teamtrace.backend.dto.student.StudentProfileResponse;
import com.teamtrace.backend.dto.teacher.UpdateTeacherProfileRequest;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.UserRepository;
import com.teamtrace.backend.util.AppTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return StudentProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role("学生")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }

    @Transactional
    public StudentProfileResponse updateProfile(Long studentId, UpdateTeacherProfileRequest request) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        user.setName(request.getName().trim());
        userRepository.save(user);
        return StudentProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role("学生")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }

    /**
     * P1：学生主动注销（软删除）。须校验登录密码。
     */
    @Transactional
    public Map<String, String> deleteAccount(Long studentId, StudentDeleteAccountRequest request) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        if (user.getRole() != User.Role.student) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("BAD_REQUEST", "密码不正确", HttpStatus.BAD_REQUEST);
        }
        user.setIsDeleted(1);
        user.setDeletedAt(AppTime.now());
        user.setStatus(0);
        userRepository.save(user);
        Map<String, String> data = new HashMap<>();
        data.put("message", "账号已注销");
        return data;
    }
}
