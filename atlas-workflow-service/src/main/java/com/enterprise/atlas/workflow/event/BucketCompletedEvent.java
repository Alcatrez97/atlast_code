package com.enterprise.atlas.workflow.event;

import com.enterprise.atlas.workflow.entity.BucketExecution;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a BucketExecution transitions to RESOLVED status.
 */
public class BucketCompletedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final BucketExecution bucketExecution;

    public BucketCompletedEvent(Object source, BucketExecution bucketExecution) {
        super(source);
        this.bucketExecution = bucketExecution;
    }

    public BucketExecution getBucketExecution() {
        return bucketExecution;
    }
}
