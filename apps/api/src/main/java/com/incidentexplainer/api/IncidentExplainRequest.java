package com.incidentexplainer.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record IncidentExplainRequest(
    @NotNull @Valid Alert alert,
    List<@NotBlank String> logs,
    Map<String, String> metrics,
    List<@Valid DeployEvent> deploys
) {

    public record Alert(
        @NotBlank String name,
        @NotBlank String service,
        @NotBlank String env,
        @NotBlank String startTime,
        @NotBlank String endTime,
        @NotBlank String description
    ) {}

    public record DeployEvent(
        @NotBlank String version,
        @NotBlank String time
    ) {}
}
