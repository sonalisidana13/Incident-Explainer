package com.incidentexplainer.api;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class KbIngestorApplication {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                 new SpringApplicationBuilder(IncidentExplainerApiApplication.class)
                     .web(WebApplicationType.NONE)
                     .run(args)) {
            KbIngestor ingestor = context.getBean(KbIngestor.class);
            ingestor.run();
        }
    }
}
