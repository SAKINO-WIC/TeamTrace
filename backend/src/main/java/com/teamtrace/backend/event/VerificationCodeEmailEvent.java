package com.teamtrace.backend.event;

public record VerificationCodeEmailEvent(String email, String code, String purposeLabel) {}
