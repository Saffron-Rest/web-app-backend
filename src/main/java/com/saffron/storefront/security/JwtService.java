package com.saffron.storefront.security;

import com.saffron.storefront.domain.AdminRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (set STOREFRONT_JWT_SECRET)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(AuthUser user) {
        return Jwts.builder()
                .subject(user.id())
                .claim("email", user.email())
                .claim("name", user.name())
                .claim("role", user.role().name())
                .claim("mustChangePassword", user.mustChangePassword())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public AuthUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("name", String.class),
                AdminRole.valueOf(claims.get("role", String.class)),
                Boolean.TRUE.equals(claims.get("mustChangePassword", Boolean.class)));
    }
}
