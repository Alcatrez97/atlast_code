package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.EventSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import com.enterprise.atlas.workflow.entity.SubscriptionStatus;

@Repository
public interface EventSubscriptionRepository extends JpaRepository<EventSubscription, String> {
    List<EventSubscription> findByEventTypeAndStatus(String eventType, SubscriptionStatus status);
    
    List<EventSubscription> findByBusinessKeyAndEventTypeAndStatus(String businessKey, String eventType, SubscriptionStatus status);

    List<EventSubscription> findByWorkflowInstanceIdOrderByCreatedAtDesc(String workflowInstanceId);
}
