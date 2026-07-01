package com.enterprise.atlas.workflow.mapper;

import com.enterprise.atlas.common.dto.WorkflowDefinitionDto;
import com.enterprise.atlas.common.dto.WorkflowVersionDto;
import com.enterprise.atlas.workflow.entity.WorkflowDefinition;
import com.enterprise.atlas.workflow.entity.WorkflowVersion;

import java.util.stream.Collectors;

public class WorkflowMapper {

    public static WorkflowDefinitionDto toDto(WorkflowDefinition entity) {
        if (entity == null) {
            return null;
        }
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setKey(entity.getKey());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setActiveVersion(entity.getActiveVersion());

        if (entity.getVersions() != null) {
            dto.setVersions(entity.getVersions().stream()
                    .map(WorkflowMapper::toDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public static WorkflowVersionDto toDto(WorkflowVersion entity) {
        if (entity == null) {
            return null;
        }
        WorkflowVersionDto dto = new WorkflowVersionDto();
        dto.setId(entity.getId());
        dto.setWorkflowDefinitionId(entity.getWorkflowDefinition().getId());
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus());
        dto.setDefinition(entity.getDefinition());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        return dto;
    }
}
