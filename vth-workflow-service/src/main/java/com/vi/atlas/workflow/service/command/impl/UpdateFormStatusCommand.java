package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class UpdateFormStatusCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(UpdateFormStatusCommand.class);

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Override
    public String getCommandType() {
        return "UPDATE_FORM_STATUS";
    }

    @Override
    public String getDisplayName() {
        return "Update Form Status";
    }

    @Override
    public String getDescription() {
        return "Updates the business lifecycle status of the CustomerForm entity in the database.";
    }

    @Override
    public String getCategory() {
        return "Lifecycle";
    }

    @Override
    public java.util.List<com.vi.atlas.workflow.dto.CommandParameterDto> getParameters() {
        com.vi.atlas.workflow.dto.CommandParameterDto statusParam = new com.vi.atlas.workflow.dto.CommandParameterDto(
                "formStatus",
                "Form Status Value",
                "select",
                true,
                "APPROVED",
                "Target lifecycle status value to assign to the customer form"
        );
        statusParam.setOptions(java.util.List.of(
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("APPROVED", "APPROVED"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("REJECTED", "REJECTED"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("PENDING_VERIFICATION", "PENDING_VERIFICATION"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("IN_PROGRESS", "IN_PROGRESS"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("SUBMITTED", "SUBMITTED"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("COMPLETED", "COMPLETED")
        ));
        return java.util.List.of(statusParam);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String contextId = (String) input.get("_contextId");
        String formStatus = getStringParam(input, "formStatus", "status");

        if (formStatus == null) {
            log.warn("UPDATE_FORM_STATUS command failed: missing formStatus or status parameter.");
            return Map.of();
        }

        String businessKey = getStringParam(input, "businessKey", "trackingId", "cafId");
        if (businessKey == null || businessKey.isBlank()) {
            if (input.get("_context") instanceof Map ctxMap) {
                Object bKey = ctxMap.get("businessKey");
                if (bKey == null) bKey = ctxMap.get("trackingId");
                if (bKey != null) businessKey = bKey.toString();
            }
        }
        String formId = (businessKey != null && !businessKey.isBlank()) ? businessKey : contextId;

        if (formId != null && !formId.isBlank()) {
            Long cafId = CustomerForm.parseCafId(formId);
            if (cafId != null) {
                final Long targetId = cafId;
                CustomerForm form = customerFormRepository.findById(targetId).orElseGet(() -> {
                    CustomerForm newForm = new CustomerForm();
                    newForm.setId(targetId);
                    return newForm;
                });
                form.setFormStatus(formStatus);
                customerFormRepository.save(form);
                log.info("Updated CustomerForm status to '{}' for cafId={}", formStatus, targetId);
            } else {
                log.warn("Could not parse formId '{}' to Long for CustomerForm update", formId);
            }
        } else {
            log.warn("Missing contextId and businessKey, skipped updating CustomerForm status.");
        }

        return Map.of("formStatus", formStatus);
    }
}
