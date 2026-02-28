package com.incidentexplainer.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/incident")
public class IncidentExplainController {

    private final IncidentExplainService incidentExplainService;

    public IncidentExplainController(IncidentExplainService incidentExplainService) {
        this.incidentExplainService = incidentExplainService;
    }

    @PostMapping("/explain")
    public IncidentExplainResponse explain(@Valid @RequestBody IncidentExplainRequest request) {
        return incidentExplainService.explain(request);
    }
}
