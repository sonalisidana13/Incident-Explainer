package com.incidentexplainer.api;

public interface EmbeddingProvider {

    int dimensions();

    float[] embed(String text);

    default String toVectorLiteral(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        if (vector.length != dimensions()) {
            throw new IllegalArgumentException(
                "vector dimension mismatch: expected " + dimensions() + " but got " + vector.length
            );
        }

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
}
