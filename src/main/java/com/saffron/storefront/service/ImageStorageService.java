package com.saffron.storefront.service;

import com.saffron.storefront.web.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.Set;

/**
 * Local-disk image storage for product photos. Files land in
 * {@code app.uploads.dir} and are served back through {@code /api/uploads/**}
 * (see {@link com.saffron.storefront.controller.PublicUploadController}). In
 * production this directory is mounted from a Docker volume so images survive
 * container redeploys.
 */
@Service
public class ImageStorageService {

    private static final SecureRandom RND = new SecureRandom();
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;

    public ImageStorageService(@Value("${app.uploads.dir:./uploads}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    /** Returns the public URL (relative path) where the image will be served. */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("File required");
        if (file.getSize() > MAX_BYTES) throw new BadRequestException("Image larger than 5 MB");
        String type = file.getContentType();
        if (type == null || !ALLOWED.contains(type.toLowerCase())) {
            throw new BadRequestException("Unsupported image type (jpeg, png, webp, gif allowed)");
        }
        String ext = switch (type.toLowerCase()) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif"  -> ".gif";
            default            -> ".jpg";
        };
        String prefix = YearMonth.now().toString();
        String name = prefix + "/" + randomToken() + ext;
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new BadRequestException("Invalid filename");
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write upload", e);
        }
        return "/api/uploads/" + name;
    }

    /** Resolve a public URL back to a disk path for serving. Returns null if not found / unsafe. */
    public Path resolveForServing(String filename) {
        if (filename == null) return null;
        Path p = root.resolve(filename).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) return null;
        return p;
    }

    private static String randomToken() {
        byte[] buf = new byte[9];
        RND.nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
