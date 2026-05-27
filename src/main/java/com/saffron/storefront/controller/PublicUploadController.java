package com.saffron.storefront.controller;

import com.saffron.storefront.service.ImageStorageService;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Serves product images written by {@link ImageStorageService}. Public so
 * the storefront and admin can render them without an auth token.
 */
@RestController
@RequestMapping("/api/uploads")
public class PublicUploadController {

    private final ImageStorageService storage;

    public PublicUploadController(ImageStorageService storage) { this.storage = storage; }

    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest req) {
        String full = req.getRequestURI();
        String prefix = req.getContextPath() + "/api/uploads/";
        if (!full.startsWith(prefix)) throw new NotFoundException("Not found");
        String relative = full.substring(prefix.length());
        Path path = storage.resolveForServing(relative);
        if (path == null) throw new NotFoundException("Not found");
        String contentType = URLConnection.guessContentTypeFromName(path.getFileName().toString());
        MediaType mt = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, mt.toString())
                .body(new FileSystemResource(path));
    }
}
