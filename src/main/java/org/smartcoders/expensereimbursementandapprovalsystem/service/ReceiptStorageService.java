package org.smartcoders.expensereimbursementandapprovalsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class ReceiptStorageService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
    );

    private final Path root;

    public ReceiptStorageService(@Value("${file.upload-dir:uploads/receipts}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory", e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is too large (max 5 MB)");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_TYPES.containsKey(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, PNG and JPG files are allowed");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(filename).normalize();

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
        return filename;
    }

    public Resource load(String filename) {
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt file not found");
        }
        return new FileSystemResource(path);
    }

    public String contentTypeFor(String filename) {
        return ALLOWED_TYPES.getOrDefault(getExtension(filename), "application/octet-stream");
    }

    private String getExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}