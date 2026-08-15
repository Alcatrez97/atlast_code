package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.common.dto.ExecutionRequestDto;
import com.vi.atlas.workflow.entity.WorkflowInstance;
import com.vi.atlas.workflow.repository.WorkflowInstanceRepository;
import com.vi.atlas.workflow.service.ExecutionService;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StartChildWorkflowCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(StartChildWorkflowCommand.class);

    @Autowired
    @Lazy
    private ExecutionService executionService;

    @Autowired
    private WorkflowInstanceRepository instanceRepository;

    @Override
    public String getCommandType() {
        return "START_CHILD_WORKFLOW";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String parentInstanceId = (String) input.get("_instanceId");
        String childWorkflowKey = getStringParam(input, "childWorkflowKey", "workflow");

        if (childWorkflowKey == null) {
            throw new IllegalArgumentException("START_CHILD_WORKFLOW command requires a 'childWorkflowKey' or 'workflow' parameter.");
        }

        log.info("Executing START_CHILD_WORKFLOW command: childWorkflowKey={}, parentInstanceId={}", childWorkflowKey, parentInstanceId);

        // Load parent businessKey
        String parentBusinessKey = null;
        if (parentInstanceId != null) {
            WorkflowInstance parentInst = instanceRepository.findById(parentInstanceId).orElse(null);
            if (parentInst != null) {
                parentBusinessKey = parentInst.getBusinessKey();
            }
        }
        if (parentBusinessKey == null) {
            parentBusinessKey = getStringParam(input, "businessKey");
        }

        // Build child context variables
        Map<String, Object> childInput = new HashMap<>();
        if (parentBusinessKey != null) {
            childInput.put("businessKey", parentBusinessKey);
        }
        if (input.get("cafId") != null) {
            childInput.put("cafId", input.get("cafId"));
        }

        // Map input variables mapped specifically for the command
        applyInputMapping(input, childInput);

        try {
            ExecutionRequestDto childRequest = new ExecutionRequestDto();
            childRequest.setBusinessKey(parentBusinessKey);
            childRequest.setContext(childInput);
            childRequest.setContextId(UUID.randomUUID().toString());
            
            var childExecution = executionService.execute(childWorkflowKey, childRequest);
            log.info("Successfully started child workflow execution: key={}, instanceId={}", childWorkflowKey, childExecution.getInstanceId());
            return Map.of("childInstanceId", childExecution.getInstanceId(), "status", childExecution.getStatus());
        } catch (Exception e) {
            log.error("Failed to start child workflow key={}: {}", childWorkflowKey, e.getMessage(), e);
            throw e;
        }
    }
}
