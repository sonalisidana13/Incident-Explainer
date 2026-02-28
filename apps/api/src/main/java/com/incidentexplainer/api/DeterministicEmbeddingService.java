package com.incidentexplainer.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeterministicEmbeddingService {

    public static final int DIM = 384;

    public float[] embed(String text) {
        float[] vector = new float[DIM];
        List<String> tokens = tokenize(text);

        if (tokens.isEmpty()) {
            vector[0] = 1.0f;
            return vector;
        }

        for (String token : tokens) {
            byte[] digest = sha256(token);

            int idx1 = toIndex(digest, 0);
            int idx2 = toIndex(digest, 8);

            float w1 = sign(digest[16]);
            float w2 = sign(digest[17]);

            vector[idx1] += w1;
            vector[idx2] += w2;
        }

        normalize(vector);
        return vector;
    }

    public String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private List<String> tokenize(String text) {
        String[] raw = text.toLowerCase().split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String token : raw) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    private byte[] sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private int toIndex(byte[] digest, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[offset + i] & 0xffL);
        }
        return (int) Math.floorMod(value, DIM);
    }

    private float sign(byte b) {
        return (b & 1) == 0 ? 1.0f : -1.0f;
    }

    private void normalize(float[] vector) {
        double normSq = 0.0;
        for (float v : vector) {
            normSq += v * v;
        }

        double norm = Math.sqrt(normSq);
        if (norm == 0.0) {
            vector[0] = 1.0f;
            return;
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
