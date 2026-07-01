package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.WorkflowDefinitionDto;
import com.enterprise.atlas.common.dto.WorkflowGraphDto;
import com.enterprise.atlas.common.dto.WorkflowVersionDto;
import com.enterprise.atlas.common.dto.WorkflowNodeDto;
import com.enterprise.atlas.common.dto.WorkflowEdgeDto;
import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;
import com.enterprise.atlas.workflow.mapper.WorkflowMapper;
import com.enterprise.atlas.workflow.repository.WorkflowDefinitionRepository;
import com.enterprise.atlas.workflow.repository.WorkflowVersionRepository;
import com.enterprise.atlas.workflow.repository.EventDefinitionRepository;
import com.enterprise.atlas.workflow.entity.EventDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
@Transactional
public class WorkflowService {

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private WorkflowVersionRepository versionRepository;

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    @Autowired
    private com.enterprise.atlas.workflow.repository.WorkflowInstanceRepository workflowInstanceRepository;

    public WorkflowDefinitionDto createWorkflowDefinition(WorkflowDefinitionDto dto) {
        if (definitionRepository.existsByKey(dto.getKey())) {
            throw new IllegalArgumentException("Workflow key '" + dto.getKey() + "' already exists.");
        }

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(UUID.randomUUID().toString());
        definition.setKey(dto.getKey());
        definition.setName(dto.getName());
        definition.setDescription(dto.getDescription());
        definition = definitionRepository.save(definition);

        // Auto-create initial draft (v1)
        createDraftVersion(definition.getId(), new WorkflowGraphDto());

        return WorkflowMapper.toDto(definition);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionDto getWorkflowDefinition(String id) {
        WorkflowDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found with ID: " + id));
        if (!definition.isActive()) {
            throw new IllegalArgumentException("Workflow definition is inactive with ID: " + id);
        }
        return WorkflowMapper.toDto(definition);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionDto> getAllWorkflowDefinitions() {
        return definitionRepository.findAllByActiveTrue().stream()
                .map(WorkflowMapper::toDto)
                .collect(Collectors.toList());
    }

    public WorkflowVersionDto createDraftVersion(String definitionId, WorkflowGraphDto definitionGraph) {
        WorkflowDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found with ID: " + definitionId));

        Integer maxVersion = versionRepository.findMaxVersionByDefinitionId(definitionId);

        WorkflowVersion version = new WorkflowVersion();
        version.setId(UUID.randomUUID().toString());
        version.setWorkflowDefinition(definition);
        version.setVersion(maxVersion + 1);
        version.setStatus("DRAFT");

        WorkflowGraphDto graphToUse = definitionGraph;
        if ((graphToUse == null || graphToUse.getNodes().isEmpty()) && maxVersion > 0) {
            Optional<WorkflowVersion> latestOpt = versionRepository.findByWorkflowDefinitionIdAndVersion(definitionId, maxVersion);
            if (latestOpt.isPresent()) {
                graphToUse = cloneGraph(latestOpt.get().getDefinition());
            }
        }
        if (graphToUse == null) {
            graphToUse = new WorkflowGraphDto();
        }
        version.setDefinition(graphToUse);
        version.setCreatedBy("Author"); // Mocked for Sprint 1
        version.setUpdatedBy("Author");

        // Save first to get the managed/merged instance in the session
        version = versionRepository.save(version);

        // Synchronize in-memory association using the managed instance reference
        definition.getVersions().add(version);

        return WorkflowMapper.toDto(version);
    }

    private WorkflowGraphDto cloneGraph(WorkflowGraphDto original) {
        if (original == null) {
            return new WorkflowGraphDto();
        }
        WorkflowGraphDto clone = new WorkflowGraphDto();

        if (original.getNodes() != null) {
            for (WorkflowNodeDto node : original.getNodes()) {
                WorkflowNodeDto nodeClone = new WorkflowNodeDto();
                nodeClone.setId(node.getId());
                nodeClone.setType(node.getType());
                nodeClone.setLabel(node.getLabel());
                if (node.getData() != null) {
                    nodeClone.setData(new HashMap<>(node.getData()));
                }
                if (node.getPosition() != null) {
                    nodeClone.setPosition(new HashMap<>(node.getPosition()));
                }
                clone.getNodes().add(nodeClone);
            }
        }

        if (original.getEdges() != null) {
            for (WorkflowEdgeDto edge : original.getEdges()) {
                WorkflowEdgeDto edgeClone = new WorkflowEdgeDto();
                edgeClone.setId(edge.getId());
                edgeClone.setSource(edge.getSource());
                edgeClone.setTarget(edge.getTarget());
                edgeClone.setLabel(edge.getLabel());
                if (edge.getData() != null) {
                    edgeClone.setData(new HashMap<>(edge.getData()));
                }
                clone.getEdges().add(edgeClone);
            }
        }

        if (original.getMetadata() != null) {
            clone.setMetadata(new HashMap<>(original.getMetadata()));
        }

        return clone;
    }

    public WorkflowVersionDto updateDraftVersion(String versionId, WorkflowGraphDto definitionGraph) {
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow version not found with ID: " + versionId));

        if (!"DRAFT".equalsIgnoreCase(version.getStatus())) {
            throw new IllegalStateException("Cannot edit a version that is in " + version.getStatus() + " status. Only DRAFT versions are mutable.");
        }

        version.setDefinition(definitionGraph);
        version.setUpdatedBy("Author");
        version = versionRepository.save(version);
        return WorkflowMapper.toDto(version);
    }

    public WorkflowVersionDto transitionVersionStatus(String versionId, String newStatus) {
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow version not found with ID: " + versionId));

        String currentStatus = version.getStatus().toUpperCase();
        String targetStatus = newStatus.toUpperCase();

        if (currentStatus.equals(targetStatus)) {
            return WorkflowMapper.toDto(version);
        }

        // Enforce transition rules
        // DRAFT -> REVIEW
        // REVIEW -> APPROVED (or DRAFT if rejected)
        // APPROVED -> PUBLISHED
        switch (targetStatus) {
            case "REVIEW":
                if (!"DRAFT".equals(currentStatus)) {
                    throw new IllegalStateException("Can only submit for Review from DRAFT status.");
                }
                validateWorkflowGraph(version.getDefinition());
                version.setStatus("REVIEW");
                break;
            case "APPROVED":
                if (!"REVIEW".equals(currentStatus)) {
                    throw new IllegalStateException("Can only Approve from REVIEW status.");
                }
                version.setStatus("APPROVED");
                break;
            case "DRAFT":
                if (!"REVIEW".equals(currentStatus) && !"APPROVED".equals(currentStatus)) {
                    throw new IllegalStateException("Can only revert to DRAFT from REVIEW or APPROVED status.");
                }
                version.setStatus("DRAFT");
                break;
            case "PUBLISHED":
                if (!"APPROVED".equals(currentStatus)) {
                    throw new IllegalStateException("Can only Publish from APPROVED status. No direct publish allowed.");
                }
                validateWorkflowGraph(version.getDefinition());
                version.setStatus("PUBLISHED");
                
                // Set as active version on the definition header
                WorkflowDefinition definition = version.getWorkflowDefinition();
                definition.setActiveVersion(version.getVersion());
                break;
            default:
                throw new IllegalArgumentException("Unknown status transition: " + newStatus);
        }

        version.setUpdatedBy("Publisher"); // Mock role
        version = versionRepository.save(version);
        return WorkflowMapper.toDto(version);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionDto getWorkflowVersion(String versionId) {
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow version not found with ID: " + versionId));
        return WorkflowMapper.toDto(version);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionDto getPublishedVersionByKey(String key) {
        WorkflowDefinition definition = definitionRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found with key: " + key));

        if (definition.getActiveVersion() == null) {
            throw new IllegalArgumentException("No active published version found for workflow key: " + key);
        }

        WorkflowVersion version = versionRepository.findByWorkflowDefinitionIdAndVersion(definition.getId(), definition.getActiveVersion())
                .orElseThrow(() -> new IllegalStateException("Published version " + definition.getActiveVersion() + " not found for workflow ID: " + definition.getId()));

        return WorkflowMapper.toDto(version);
    }

    public void deleteWorkflowDefinition(String id) {
        WorkflowDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found with ID: " + id));
        definition.setActive(false);
        definition.setActiveVersion(null);
        definitionRepository.save(definition);
    }

    public void deleteWorkflowVersion(String versionId) {
        WorkflowVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow version not found with ID: " + versionId));

        if ("PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new IllegalStateException("Cannot delete a PUBLISHED workflow version.");
        }

        long instanceCount = workflowInstanceRepository.countByWorkflowVersionId(versionId);
        if (instanceCount == 0) {
            WorkflowDefinition definition = version.getWorkflowDefinition();
            if (definition.getActiveVersion() != null && definition.getActiveVersion().equals(version.getVersion())) {
                definition.setActiveVersion(null);
                definitionRepository.save(definition);
            }

            // Synchronize in-memory association
            definition.getVersions().remove(version);

            versionRepository.delete(version);
        } else {
            version.setStatus("ARCHIVED");
            versionRepository.save(version);

            WorkflowDefinition definition = version.getWorkflowDefinition();
            if (definition.getActiveVersion() != null && definition.getActiveVersion().equals(version.getVersion())) {
                definition.setActiveVersion(null);
                definitionRepository.save(definition);
            }
        }
    }

    public void validateWorkflowGraph(WorkflowGraphDto graph) {
        if (graph == null || graph.getNodes() == null) {
            return;
        }

        for (WorkflowNodeDto node : graph.getNodes()) {
            String type = node.getType() != null ? node.getType().toUpperCase() : "";
            if ("WAIT_EVENT".equals(type)) {
                String eventType = null;
                if (node.getData() != null) {
                    eventType = (String) node.getData().get("eventType");
                }
                if (eventType == null || eventType.trim().isEmpty()) {
                    throw new IllegalArgumentException("Wait event node '" + node.getId() + "' has no event type configured.");
                }
                
                Optional<EventDefinition> eventOpt = eventDefinitionRepository.findByEventKey(eventType);
                if (eventOpt.isEmpty()) {
                    throw new IllegalArgumentException("Wait event node '" + node.getId() + "' references unregistered event type: '" + eventType + "'");
                }
                if (!eventOpt.get().isActive()) {
                    throw new IllegalArgumentException("Wait event node '" + node.getId() + "' references inactive event type: '" + eventType + "'");
                }
            } else if ("COMMAND".equals(type)) {
                String commandType = null;
                if (node.getData() != null) {
                    commandType = (String) node.getData().get("commandType");
                    if (commandType == null) {
                        commandType = (String) node.getData().get("type");
                    }
                }
                if (commandType != null && ("EMIT_EVENT".equalsIgnoreCase(commandType) || "PUBLISH_EVENT".equalsIgnoreCase(commandType))) {
                    String eventKey = null;
                    if (node.getData() != null) {
                        eventKey = (String) node.getData().get("eventKey");
                        if (eventKey == null || eventKey.trim().isEmpty()) {
                            eventKey = (String) node.getData().get("eventType");
                        }
                    }
                    if (eventKey == null || eventKey.trim().isEmpty()) {
                        throw new IllegalArgumentException("Emit event command node '" + node.getId() + "' has no event key configured.");
                    }

                    Optional<EventDefinition> eventOpt = eventDefinitionRepository.findByEventKey(eventKey);
                    if (eventOpt.isEmpty()) {
                        throw new IllegalArgumentException("Emit event command node '" + node.getId() + "' references unregistered event key: '" + eventKey + "'");
                    }
                }
            }
        }
    }
}
