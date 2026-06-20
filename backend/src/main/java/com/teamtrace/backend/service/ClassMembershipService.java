package com.teamtrace.backend.service;

import com.teamtrace.backend.entity.ClassStudent;
import com.teamtrace.backend.exception.BusinessException;
import com.teamtrace.backend.repository.ClassStudentRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级成员关系校验。后续「学生查看/接收班级任务」等接口应依赖本服务，确保仅有效在班学生可操作。
 */
@Service
public class ClassMembershipService {

    private final ClassStudentRepository classStudentRepository;

    public ClassMembershipService(ClassStudentRepository classStudentRepository) {
        this.classStudentRepository = classStudentRepository;
    }

    @Transactional(readOnly = true)
    public boolean isActiveStudentInClass(Long studentId, Long classId) {
        return classStudentRepository
                .findByClassIdAndStudentId(classId, studentId)
                .filter(cs -> cs.getIsDeleted() == null || cs.getIsDeleted() == 0)
                .isPresent();
    }

    /**
     * 学生必须当前在该班级且未被移除，否则拒绝（用于任务等业务）。
     */
    public void requireActiveStudentInClass(Long studentId, Long classId) {
        if (!isActiveStudentInClass(studentId, classId)) {
            throw new BusinessException("FORBIDDEN", "你不在该班级中，无法查看或接收班级任务", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 可选：返回关联行（例如需要 joinTime 等），不存在或非在班返回 empty。
     */
    @Transactional(readOnly = true)
    public Optional<ClassStudent> findActiveMembership(Long studentId, Long classId) {
        return classStudentRepository.findByClassIdAndStudentId(classId, studentId).filter(cs -> cs.getIsDeleted() == null || cs.getIsDeleted() == 0);
    }
}
