package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.admin.ChangePasswordRequest;
import com.teamtrace.backend.dto.teacher.TeacherProfileResponse;
import com.teamtrace.backend.dto.teacher.UpdateTeacherProfileRequest;
import com.teamtrace.backend.entity.User;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TeacherProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getProfile(Long teacherId) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return TeacherProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role("教师")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }

    @Transactional
    public TeacherProfileResponse updateProfile(Long teacherId, UpdateTeacherProfileRequest request) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        user.setName(request.getName().trim());
        userRepository.save(user);
        return TeacherProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role("教师")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }

    @Transactional
    public void changePassword(Long teacherId, ChangePasswordRequest request) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED));
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException("UNAUTHORIZED", "用户不存在", HttpStatus.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("BAD_REQUEST", "旧密码不正确", HttpStatus.BAD_REQUEST);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
