package com.vi.atlas.workflow.controller;

import com.vi.atlas.workflow.service.CafJourneyIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public Ingestion Gateway for CAF and Document submissions.
 * Supports out-of-order API arrival and handles staging before golden record convergence.
 */
@RestController
@RequestMapping("/api/caf-journey")
@CrossOrigin(origins = "*")
public class CafJourneyIngestionController {

    @Autowired
    private CafJourneyIngestionService ingestionService;

    /**
     * Submit Documents API (Can arrive before or after Submit CAF).
     */
    @PostMapping("/submit-docs")
    public ResponseEntity<CafJourneyIngestionService.IngestionResult> submitDocuments(@RequestBody Map<String, Object> body) {
        String trackingId = (String) body.get("trackingId");
        if (trackingId == null || trackingId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Integer circleId = 1;
        if (body.get("circleId") instanceof Number num) {
            circleId = num.intValue();
        }

        CafJourneyIngestionService.IngestionResult result =
                ingestionService.stageAndIngestDocuments(trackingId, circleId, body);

        return ResponseEntity.ok(result);
    }

    /**
     * Submit CAF API (Can arrive before or after Submit Documents).
     */
    @PostMapping("/submit-caf")
    public ResponseEntity<CafJourneyIngestionService.IngestionResult> submitCaf(@RequestBody Map<String, Object> body) {
        String trackingId = (String) body.get("trackingId");
        if (trackingId == null || trackingId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Integer circleId = 1;
        if (body.get("circleId") instanceof Number num) {
            circleId = num.intValue();
        }

        CafJourneyIngestionService.IngestionResult result =
                ingestionService.stageAndIngestCaf(trackingId, circleId, body);

        return ResponseEntity.ok(result);
    }

    /**
     * Create Family Group API.
     */
    @PostMapping("/create-family-group")
    @SuppressWarnings("unchecked")
    public ResponseEntity<CafJourneyIngestionService.IngestionResult> createFamilyGroup(@RequestBody Map<String, Object> body) {
        String groupId = (String) body.get("groupId");
        if (groupId == null || groupId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String primaryTrackingId = (String) body.get("primaryTrackingId");
        List<String> memberTrackingIds = (List<String>) body.get("memberTrackingIds");

        CafJourneyIngestionService.IngestionResult result =
                ingestionService.stageAndIngestFamilyGroup(groupId, primaryTrackingId, memberTrackingIds, body);

        return ResponseEntity.ok(result);
    }

    /**
     * Get Journey & Staging status for tracking ID.
     */
    @GetMapping("/status/{trackingId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String trackingId) {
        Map<String, Object> status = ingestionService.getJourneyStatus(trackingId);
        return ResponseEntity.ok(status);
    }
}
