package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.EventSubscription;
import com.enterprise.atlas.workflow.entity.TaskInstance;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.entity.SubscriptionStatus;
import com.enterprise.atlas.workflow.repository.EventSubscriptionRepository;
import com.enterprise.atlas.workflow.repository.TaskInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class EventRoutingService {

    private static final Logger log = LoggerFactory.getLogger(EventRoutingService.class);

    @Autowired
    private EventSubscriptionRepository eventSubscriptionRepository;

    @Autowired
    private TaskInstanceRepository taskInstanceRepository;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private com.enterprise.atlas.workflow.repository.EventDefinitionRepository eventDefinitionRepository;

    /**
     * Resolves the correlation business key using configured EventDefinition path.
     */
    public String resolveCorrelationKey(String eventType, String businessKey, Map<String, Object> payload) {
        if (eventType == null) return businessKey;

        java.util.Optional<com.enterprise.atlas.workflow.entity.EventDefinition> defOpt = eventDefinitionRepository.findByEventKey(eventType);
        if (defOpt.isPresent()) {
            String path = defOpt.get().getCorrelationKeyPath();
            if (path != null && !path.isBlank()) {
                Map<String, Object> combined = new java.util.HashMap<>();
                if (payload != null) {
                    combined.putAll(payload);
                    combined.put("payload", payload);
                }
                if (businessKey != null) {
                    combined.put("businessKey", businessKey);
                }

                String resolved = extractValueByPath(combined, path);
                log.info("Resolving correlation key: eventType={}, path='{}', originalKey='{}', resolvedKey='{}', payload={}",
                         eventType, path, businessKey, resolved, payload);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            }
        }
        return businessKey;
    }

    private String extractValueByPath(Map<String, Object> data, String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current != null ? String.valueOf(current) : null;
    }

    /**
     * Routes an inbound event to match and trigger active subscriptions.
     *
     * @param eventType the inbound event type (e.g. PAYMENT_RECEIVED)
     * @param businessKey the correlation business key (e.g. CAF123)
     * @param payload key-value context attributes of the event
     */
    public void routeEvent(String eventType, String businessKey, Map<String, Object> payload) {
        String correlatedKey = resolveCorrelationKey(eventType, businessKey, payload);
        log.info("Inbound event received - type: {}, originalKey: {}, resolvedKey: {}, payload: {}", 
                 eventType, businessKey, correlatedKey, payload);

        List<EventSubscription> activeSubs = eventSubscriptionRepository
                .findByBusinessKeyAndEventTypeAndStatus(correlatedKey, eventType, SubscriptionStatus.ACTIVE);

        if (activeSubs.isEmpty()) {
            log.warn("No active event subscriptions found for correlatedKey: {} and eventType: {}", correlatedKey, eventType);
            return;
        }

        log.info("Found {} active subscription(s) matching correlatedKey: {}, eventType: {}", activeSubs.size(), correlatedKey, eventType);

        for (EventSubscription sub : activeSubs) {
            log.info("Checking subscription filter matches for sub ID: {}, filters: {}", sub.getId(), sub.getFilterAttributes());
            if (matchesFilters(sub.getFilterAttributes(), payload)) {
                log.info("Correlation matched successfully for event subscription ID: {}", sub.getId());

                // 1. Mark subscription as TRIGGERED
                sub.setStatus("TRIGGERED");
                eventSubscriptionRepository.save(sub);

                WorkflowInstance instance = sub.getWorkflowInstance();

                // 2. Resolve active task instance waiting on this node and mark completed
                try {
                    List<TaskInstance> tasks = taskInstanceRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instance.getId());
                    TaskInstance targetTask = tasks.stream()
                            .filter(t -> t.getStatus().equalsIgnoreCase("WAITING") && t.getId().contains(sub.getTargetNodeId()))
                            .findFirst()
                            .orElse(null);

                    if (targetTask == null) {
                        // fallback to find any WAITING task
                        targetTask = tasks.stream()
                                .filter(t -> t.getStatus().equalsIgnoreCase("WAITING"))
                                .findFirst()
                                .orElse(null);
                    }

                    if (targetTask != null) {
                        targetTask.setStatus("COMPLETED");
                        targetTask.setOutputData(payload);
                        targetTask.setCompletedAt(LocalDateTime.now());
                        taskInstanceRepository.save(targetTask);
                        log.info("Marked TaskInstance {} as COMPLETED", targetTask.getId());
                    }
                } catch (Exception ex) {
                    log.warn("Error updating TaskInstance status during event routing: {}", ex.getMessage());
                }

                // 3. Resume execution through ExecutionService
                try {
                    executionService.resume(instance.getId(), payload);
                    log.info("Resumed workflow instance: {} successfully.", instance.getId());
                } catch (Exception ex) {
                    log.error("Failed to resume instance {} on event: {}", instance.getId(), ex.getMessage(), ex);
                }
            }
        }
    }

    private boolean matchesFilters(Map<String, Object> filterAttributes, Map<String, Object> payload) {
        if (filterAttributes == null || filterAttributes.isEmpty()) {
            return true;
        }
        if (payload == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : filterAttributes.entrySet()) {
            Object expected = entry.getValue();
            Object actual = payload.get(entry.getKey());
            if (expected == null && actual == null) continue;
            if (expected == null || actual == null) return false;
            if (!String.valueOf(expected).equals(String.valueOf(actual))) {
                return false;
            }
        }
        return true;
    }
}
