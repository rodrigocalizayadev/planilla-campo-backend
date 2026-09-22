package com.planillacampo.planilla_campo.auth.services;

import com.planillacampo.planilla_campo.auth.entities.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.AbstractDeserializer;
import io.jsonwebtoken.io.AbstractSerializer;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;
    private final ObjectMapper objectMapper;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours:24}") long expirationHours,
            ObjectMapper objectMapper) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
        this.objectMapper = objectMapper;
    }

    public String generarToken(Usuario usuario) {
        Instant now = Instant.now();
        return Jwts.builder()
                .json(new Jackson3Serializer(objectMapper))
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parsearClaims(String token) {
        return Jwts.parser()
                .json(new Jackson3Deserializer(objectMapper))
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extraerUsuarioId(String token) {
        return Long.valueOf(parsearClaims(token).getSubject());
    }

    private static class Jackson3Serializer extends AbstractSerializer<Map<String, ?>> {
        private final ObjectMapper mapper;

        Jackson3Serializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected void doSerialize(Map<String, ?> value, OutputStream out) throws Exception {
            mapper.writeValue(out, value);
        }
    }

    private static class Jackson3Deserializer extends AbstractDeserializer<Map<String, ?>> {
        private final ObjectMapper mapper;

        Jackson3Deserializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Map<String, ?> doDeserialize(Reader reader) throws Exception {
            return mapper.readValue(reader, Map.class);
        }
    }
}
