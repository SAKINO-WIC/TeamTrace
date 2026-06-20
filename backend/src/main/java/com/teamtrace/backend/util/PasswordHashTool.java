package com.teamtrace.backend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashTool {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: PasswordHashTool <plainPassword>");
            System.exit(2);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        System.out.println(encoder.encode(args[0]));
    }
}

