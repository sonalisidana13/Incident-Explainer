package com.incidentexplainer.api;

public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final int DIM = 384;

    private final String model;

    public OpenAiEmbeddingProvider(String model) {
        this.model = model;
    }

    @Override
    public int dimensions() {
        return DIM;
    }

    @Override
    public float[] embed(String text) {
        // TODO: Implement OpenAI embeddings call using configured model and map to 384 dimensions.
        // Suggested contract:
        // 1) Send `text` to embeddings API.
        // 2) Validate vector dimensionality.
        // 3) Return float[384] for pgvector storage/search.
        throw new UnsupportedOperationException(
            "OpenAiEmbeddingProvider is a skeleton. Configure EMBEDDING_PROVIDER=fake or implement API integration for model=" + model
        );
    }

    public String model() {
        return model;
    }
}
