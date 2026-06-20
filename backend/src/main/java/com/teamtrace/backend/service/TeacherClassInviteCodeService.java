package com.teamtrace.backend.service;

import com.teamtrace.backend.dto.teacher.ClassInviteCodeResponse;
import com.teamtrace.backend.entity.ClassEntity;
import com.teamtrace.backend.entity.ClassInviteCode;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassInviteCodeRepository;
import com.teamtrace.backend.repository.ClassRepository;
import com.teamtrace.backend.util.AppTime;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherClassInviteCodeService {

    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_USED = 1;
    private static final int STATUS_REFRESHED_INVALID = 2;

    private final ClassRepository classRepository;
    private final ClassInviteCodeRepository classInviteCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TeacherClassInviteCodeService(
            ClassRepository classRepository,
            ClassInviteCodeRepository classInviteCodeRepository) {
        this.classRepository = classRepository;
        this.classInviteCodeRepository = classInviteCodeRepository;
    }

    @Transactional
    public ClassInviteCodeResponse generateOrRefresh(Long teacherId, Long classId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND));

        if (clazz.getIsDeleted() != null && clazz.getIsDeleted() == 1) {
            throw new BusinessException("NOT_FOUND", "班级不存在", HttpStatus.NOT_FOUND);
        }
        if (clazz.getStatus() != null && clazz.getStatus() == 0) {
            throw new BusinessException("BAD_REQUEST", "班级已结束", HttpStatus.BAD_REQUEST);
        }
        if (clazz.getTeacherId() == null || !clazz.getTeacherId().equals(teacherId)) {
            throw new BusinessException("FORBIDDEN", "无权限", HttpStatus.FORBIDDEN);
        }

        // 同一班级只保留 1 个有效邀请码；刷新时把旧的可用码都置为 status=2
        classInviteCodeRepository.bulkUpdateStatusByClassIdAndStatus(classId, STATUS_UNUSED, STATUS_REFRESHED_INVALID);
        classInviteCodeRepository.bulkUpdateStatusByClassIdAndStatus(classId, STATUS_USED, STATUS_REFRESHED_INVALID);

        LocalDateTime now = AppTime.now();
        LocalDateTime expireAt = now.plusHours(24);

        ClassInviteCode saved = insertWithRetry(classId, expireAt);
        return ClassInviteCodeResponse.builder()
                .classId(saved.getClassId())
                .code(saved.getCode())
                .status(saved.getStatus())
                .expireAt(saved.getExpireAt())
                .build();
    }

    private ClassInviteCode insertWithRetry(Long classId, LocalDateTime expireAt) {
        int maxAttempts = 8;
        for (int i = 0; i < maxAttempts; i++) {
            String code = generateCode();
            ClassInviteCode cic = new ClassInviteCode();
            cic.setClassId(classId);
            cic.setCode(code);
            cic.setStatus(STATUS_UNUSED);
            cic.setExpireAt(expireAt);
            try {
                return classInviteCodeRepository.save(cic);
            } catch (DataIntegrityViolationException ex) {
                // code 唯一约束冲突，重试
            }
        }
        throw new BusinessException("INTERNAL_ERROR", "生成邀请码失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆字符
        int length = 8;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
