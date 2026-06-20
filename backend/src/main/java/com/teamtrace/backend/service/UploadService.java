package com.teamtrace.backend.service;

import com.teamtrace.backend.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "zip", "rar"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final Set<String> ZIP_BASED_EXTENSIONS = Set.of("docx", "xlsx", "pptx", "zip");
    private static final Set<String> OLE_BASED_EXTENSIONS = Set.of("doc", "xls", "ppt");

    private final Path uploadDir;

    public UploadService(@Value("${teamtrace.upload.dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        ensureUploadDirectory();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("BAD_REQUEST", "Uploaded file cannot be empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("BAD_REQUEST", "File size cannot exceed 10MB", HttpStatus.BAD_REQUEST);
        }

        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("BAD_REQUEST", "Unsupported file type: " + ext, HttpStatus.BAD_REQUEST);
        }
        validateFileSignature(file, ext);

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = uploadDir.resolve(storedName);

        try {
            ensureUploadDirectory();
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException("INTERNAL_ERROR", "Failed to save file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return "/uploads/" + storedName;
    }

    private void ensureUploadDirectory() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new BusinessException("INTERNAL_ERROR", "Cannot create upload directory", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void validateFileSignature(MultipartFile file, String ext) {
        byte[] head = readHead(file, 16);
        boolean valid = switch (ext) {
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWithAscii(head, "GIF87a") || startsWithAscii(head, "GIF89a");
            case "bmp" -> startsWithAscii(head, "BM");
            case "webp" -> startsWithAscii(head, "RIFF") && head.length >= 12
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case "pdf" -> startsWithAscii(head, "%PDF-");
            case "rar" -> startsWith(head, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07);
            default -> true;
        };

        if (ZIP_BASED_EXTENSIONS.contains(ext)) {
            valid = startsWithAscii(head, "PK");
        } else if (OLE_BASED_EXTENSIONS.contains(ext)) {
            valid = startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
        } else if (TEXT_EXTENSIONS.contains(ext)) {
            valid = !looksBinary(head);
        }

        if (!valid) {
            throw new BusinessException("BAD_REQUEST", "File content does not match extension", HttpStatus.BAD_REQUEST);
        }
    }

    private byte[] readHead(MultipartFile file, int length) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(length);
        } catch (IOException e) {
            throw new BusinessException("BAD_REQUEST", "Cannot read uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean startsWithAscii(byte[] bytes, String prefix) {
        if (bytes.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (bytes[i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int... values) {
        if (bytes.length < values.length) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            if ((bytes[i] & 0xFF) != values[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksBinary(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
