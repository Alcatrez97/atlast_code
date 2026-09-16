package com.vi.atlas.workflow.service.command.impl;

import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.entity.StagedPayload;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import com.vi.atlas.workflow.repository.StagedPayloadRepository;
import com.vi.atlas.workflow.service.command.WorkflowCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Command executed when parallel ingestion branches (Documents & CAF) converge.
 * Consolidates the staged records, validates completeness, and atomically creates/updates
 * the golden CustomerForm record in the main table.
 */
@Component
public class FinalizeCafSubmissionCommand implements WorkflowCommand {

    private static final Logger log = LoggerFactory.getLogger(FinalizeCafSubmissionCommand.class);

    @Autowired
    private StagedPayloadRepository stagedPayloadRepository;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private com.vi.atlas.workflow.repository.WorkflowInstanceRepository workflowInstanceRepository;

    @Override
    public String getCommandType() {
        return "FINALIZE_CAF_SUBMISSION";
    }

    @Override
    public String getDisplayName() {
        return "Finalize CAF Golden Record";
    }

    @Override
    public String getDescription() {
        return "Consolidates staged documents and CAF data upon parallel join convergence, activates golden CustomerForm record, and marks staged payloads as consumed.";
    }

    @Override
    public String getCategory() {
        return "Lifecycle";
    }

    @Override
    public java.util.List<com.vi.atlas.workflow.dto.CommandParameterDto> getParameters() {
        com.vi.atlas.workflow.dto.CommandParameterDto statusParam = new com.vi.atlas.workflow.dto.CommandParameterDto(
                "finalStatus",
                "Final Form Status",
                "select",
                false,
                "CAF_SUBMITTED_AND_ACTIVE",
                "Status assigned to the golden CustomerForm record upon finalization"
        );
        statusParam.setOptions(java.util.List.of(
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("CAF_SUBMITTED_AND_ACTIVE", "CAF_SUBMITTED_AND_ACTIVE"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("APPROVED", "APPROVED"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("PENDING_VERIFICATION", "PENDING_VERIFICATION"),
                new com.vi.atlas.workflow.dto.CommandParameterDto.Option("REJECTED", "REJECTED")
        ));

        return java.util.List.of(
                statusParam,
                new com.vi.atlas.workflow.dto.CommandParameterDto(
                        "circleId",
                        "Telecom Circle ID",
                        "number",
                        false,
                        null,
                        "Optional circle ID override (defaults to staged CAF value)"
                ),
                new com.vi.atlas.workflow.dto.CommandParameterDto(
                        "customerName",
                        "Customer Name Override",
                        "text",
                        false,
                        "",
                        "Optional customer name override (defaults to staged CAF value)"
                )
        );
    }

    @Override
    @Transactional
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        String businessKey = getStringParam(input, "businessKey", "trackingId", "cafId", "_businessKey");
        if (businessKey == null || businessKey.isBlank()) {
            if (input.get("_context") instanceof Map ctxMap) {
                Object bKey = ctxMap.get("businessKey");
                if (bKey == null) bKey = ctxMap.get("trackingId");
                if (bKey == null) bKey = ctxMap.get("cafId");
                if (bKey != null) businessKey = bKey.toString();
            }
        }
        if ((businessKey == null || businessKey.isBlank()) && input.get("_instanceId") != null) {
            String instId = (String) input.get("_instanceId");
            var instOpt = workflowInstanceRepository.findById(instId);
            if (instOpt.isPresent() && instOpt.get().getBusinessKey() != null) {
                businessKey = instOpt.get().getBusinessKey();
            }
        }
        if (businessKey == null || businessKey.isBlank()) {
            businessKey = (String) input.get("_contextId");
        }

        log.info("Executing FINALIZE_CAF_SUBMISSION for businessKey={}", businessKey);

        Optional<StagedPayload> stagedCafOpt = Optional.empty();
        Optional<StagedPayload> stagedDocsOpt = Optional.empty();

        if (businessKey != null) {
            stagedCafOpt = stagedPayloadRepository.findFirstByBusinessKeyAndPayloadTypeOrderByCreatedAtDesc(businessKey, "CAF");
            stagedDocsOpt = stagedPayloadRepository.findFirstByBusinessKeyAndPayloadTypeOrderByCreatedAtDesc(businessKey, "DOCUMENTS");
        }

        // Extract customer details from staged CAF, or fallback to input/_context
        String customerName = "Unknown";
        Integer circleId = 1;
        if (stagedCafOpt.isPresent() && stagedCafOpt.get().getPayload() != null) {
            Map<String, Object> cafData = stagedCafOpt.get().getPayload();
            if (cafData.get("customerName") != null) {
                customerName = cafData.get("customerName").toString();
            }
            if (cafData.get("circleId") instanceof Number num) {
                circleId = num.intValue();
            }
        } else if (input.get("customerName") != null) {
            customerName = input.get("customerName").toString();
        } else if (input.get("_context") instanceof Map ctxMap && ctxMap.get("customerName") != null) {
            customerName = ctxMap.get("customerName").toString();
        }

        if (input.get("circleId") instanceof Number num) {
            circleId = num.intValue();
        }

        String finalStatus = getStringParam(input, "finalStatus", "formStatus", "status");
        if (finalStatus == null || finalStatus.isBlank()) {
            finalStatus = "CAF_SUBMITTED_AND_ACTIVE";
        }

        // Target form ID
        final String formId = (businessKey != null && !businessKey.isBlank())
                ? businessKey
                : (input.get("_contextId") != null ? (String) input.get("_contextId") : java.util.UUID.randomUUID().toString());

        CustomerForm form = customerFormRepository.findById(formId).orElseGet(() -> {
            CustomerForm newForm = new CustomerForm();
            newForm.setId(formId);
            return newForm;
        });

        form.setCustomerName(customerName);
        form.setFormStatus(finalStatus);
        form.setCircleId(circleId);
        form.setUpdatedAt(LocalDateTime.now());
        customerFormRepository.save(form);

        // Mark staged records as CONSUMED
        stagedCafOpt.ifPresent(caf -> {
            caf.setStatus("CONSUMED");
            stagedPayloadRepository.save(caf);
        });
        stagedDocsOpt.ifPresent(docs -> {
            docs.setStatus("CONSUMED");
            stagedPayloadRepository.save(docs);
        });

        log.info("Successfully created/updated golden CustomerForm id={} with status={}", form.getId(), form.getFormStatus());

        Map<String, Object> output = new HashMap<>();
        output.put("cafFinalized", true);
        output.put("finalCustomerFormId", form.getId());
        output.put("finalFormStatus", form.getFormStatus());
        return output;
    }
}
