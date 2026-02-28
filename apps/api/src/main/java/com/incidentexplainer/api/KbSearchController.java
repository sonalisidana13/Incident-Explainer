package com.incidentexplainer.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kb")
@Validated
public class KbSearchController {

    private final RetrievalService retrievalService;

    public KbSearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/search")
    public KbSearchResponse search(
        @RequestParam("q") @NotBlank String query,
        @RequestParam(value = "k", defaultValue = "5") @Min(1) @Max(50) int k,
        @RequestParam(value = "kind", required = false) String kind,
        @RequestParam(value = "service", required = false) String service
    ) {
        List<KbSearchResult> results = retrievalService.search(query, k, kind, service);
        return new KbSearchResponse(query, k, kind, service, results);
    }
}
