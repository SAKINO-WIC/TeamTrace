package com.teamtrace.backend.domain.score;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 任务级加权总分：互评（组内收到的平均分，归一到 0–100）与教师评分（0–100）按任务权重线性组合。
 * 一人成组或未开启互评时，互评分量不适用，总分为教师分（见 {@link #weightedTotal100}）。
 */
public final class WeightedScoreFormulas {

    public static final int SCALE_TOTAL = 2;

    private WeightedScoreFormulas() {}

    /**
     * 将互评原始分（0～maxPeer）换算为 0～100 分制。
     *
     * @param peerAverageReceived 收到的互评分数平均值；{@code maxPeer} 须 &gt; 0
     */
    public static BigDecimal peerAverageTo100(BigDecimal peerAverageReceived, int maxPeer) {
        if (peerAverageReceived == null || maxPeer <= 0) {
            return null;
        }
        return peerAverageReceived
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxPeer), 10, RoundingMode.HALF_UP)
                .setScale(SCALE_TOTAL, RoundingMode.HALF_UP);
    }

    /**
     * @param peerApplicable  组内互评适用（任务开启互评且组内有效成员 ≥ 2）
     * @param peerOn100       互评归一后得分，不适用时为 {@code null}
     * @param teacherOn100    教师评分（0–100），未打分时为 {@code null}
     * @param peerWeight        任务配置互评权重
     * @param teacherWeight     任务配置教师权重
     * @return 加权总分（0–100），分量不足时返回 {@code null}
     */
    public static BigDecimal weightedTotal100(
            boolean peerApplicable,
            BigDecimal peerOn100,
            BigDecimal teacherOn100,
            BigDecimal peerWeight,
            BigDecimal teacherWeight) {
        BigDecimal wP = peerWeight != null ? peerWeight : BigDecimal.ZERO;
        BigDecimal wT = teacherWeight != null ? teacherWeight : BigDecimal.ZERO;

        if (!peerApplicable) {
            return teacherOn100 != null ? teacherOn100.setScale(SCALE_TOTAL, RoundingMode.HALF_UP) : null;
        }
        if (peerOn100 == null || teacherOn100 == null) {
            return null;
        }
        BigDecimal sum = wP.multiply(peerOn100).add(wT.multiply(teacherOn100));
        return sum.setScale(SCALE_TOTAL, RoundingMode.HALF_UP);
    }
}
