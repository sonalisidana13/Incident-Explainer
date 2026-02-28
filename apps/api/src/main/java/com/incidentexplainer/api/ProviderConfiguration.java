package com.incidentexplainer.api;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfiguration {

    @Bean
    public EmbeddingProvider embeddingProvider(
        @Value("${embedding.provider:${EMBEDDING_PROVIDER:fake}}") String configuredProvider,
        @Value("${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}") String openAiModel
    ) {
        String provider = normalize(configuredProvider);
        return switch (provider) {
            case "fake" -> new FakeEmbeddingProvider();
            case "openai" -> new OpenAiEmbeddingProvider(openAiModel);
            default -> throw new IllegalStateException(
                "Unsupported EMBEDDING_PROVIDER='" + configuredProvider + "'. Use fake|openai."
            );
        };
    }

    @Bean
    public LlmProvider llmProvider(
        @Value("${llm.provider:${LLM_PROVIDER:heuristic}}") String configuredProvider,
        @Value("${OPENAI_LLM_MODEL:gpt-4.1-mini}") String openAiModel
    ) {
        String provider = normalize(configuredProvider);
        return switch (provider) {
            case "heuristic" -> new NoopHeuristicExplainer();
            case "openai" -> new OpenAiLlmProvider(openAiModel);
            default -> throw new IllegalStateException(
                "Unsupported LLM_PROVIDER='" + configuredProvider + "'. Use heuristic|openai."
            );
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
