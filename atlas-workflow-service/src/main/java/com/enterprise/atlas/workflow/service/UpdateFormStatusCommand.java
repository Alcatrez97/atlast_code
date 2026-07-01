package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.workflow.entity.CustomerForm;
import com.enterprise.atlas.workflow.repository.CustomerFormRepository;
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
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String contextId = (String) input.get("_contextId");
        String formStatus = (String) input.get("formStatus");
        if (formStatus == null) {
            formStatus = (String) input.get("status");
        }

        if (formStatus == null || formStatus.isBlank()) {
            log.warn("UPDATE_FORM_STATUS command failed: missing formStatus or status parameter.");
            return Map.of();
        }

        if (contextId != null && !contextId.isBlank()) {
            Optional<CustomerForm> formOpt = customerFormRepository.findById(contextId);
            if (formOpt.isPresent()) {
                CustomerForm form = formOpt.get();
                form.setFormStatus(formStatus);
                customerFormRepository.save(form);
                log.info("Updated CustomerForm status to '{}' for formId={}", formStatus, contextId);
            } else {
                log.warn("CustomerForm not found for formId={}", contextId);
            }
        } else {
            log.warn("Missing contextId, skipped updating CustomerForm status.");
        }

        return Map.of("formStatus", formStatus);
    }
}
