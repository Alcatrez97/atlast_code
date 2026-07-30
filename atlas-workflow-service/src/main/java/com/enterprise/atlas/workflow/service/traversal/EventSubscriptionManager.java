package com.enterprise.atlas.workflow.service.traversal;

import com.enterprise.atlas.workflow.entity.EventSubscription;
import com.enterprise.atlas.workflow.entity.SubscriptionStatus;
import com.enterprise.atlas.workflow.entity.WorkflowInstance;
import com.enterprise.atlas.workflow.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and manages {@link EventSubscription} records that allow suspended
 * workflow nodes (BUCKET, WAIT_EVENT, COMMAND-async, SUB_WORKFLOW) to be
 * resumed when a matching event arrives.
 *
 * <p>Logic copied verbatim from {@code GraphTraversalEngine#createEventSubscription}.
 */
@Component
public class EventSubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(EventSubscriptionManager.class);

    @Autowired
    private EventSubscriptionRepository eventSubscriptionRepository;

    /**
     * Creates an active {@link EventSubscription} for the given instance and event type,
     * unless one already exists for the same business-key / event-type / target-node triple.
     *
     * @param instance     the workflow instance being suspended (no-op if {@code null})
     * @param eventType    the event type string the subscription listens for
     * @param targetNodeId the node ID to resume when the event fires
     * @param filters      optional filter attributes for event matching
     */
    public void createEventSubscription(WorkflowInstance instance,
                                         String eventType,
                                         String targetNodeId,
                                         Map<String, Object> filters) {
        if (instance == null) return;

        // Idempotency: do not duplicate an existing active subscription for the same target
        List<EventSubscription> existing = eventSubscriptionRepository
                .findByBusinessKeyAndEventTypeAndStatus(
                        instance.getBusinessKey(), eventType, SubscriptionStatus.ACTIVE);
        boolean exists = existing.stream()
                .anyMatch(sub -> sub.getTargetNodeId().equals(targetNodeId));
        if (exists) return;

        EventSubscription sub = new EventSubscription();
        sub.setId(UUID.randomUUID().toString());
        sub.setBusinessKey(instance.getBusinessKey());
        sub.setEventType(eventType);
        sub.setTargetNodeId(targetNodeId);
        sub.setWorkflowInstance(instance);
        sub.setFilterAttributes(filters != null ? filters : Map.of());
        sub.setStatus("ACTIVE");
        eventSubscriptionRepository.saveAndFlush(sub);

        log.info("Created EventSubscription: instanceId={}, eventType={}, targetNode={}",
                instance.getId(), eventType, targetNodeId);
    }
}
