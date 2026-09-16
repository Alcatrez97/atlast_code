package com.vi.atlas.workflow.dto;

import java.util.ArrayList;
import java.util.List;

public class CommandMetadataDto {

    private String type;
    private String displayName;
    private String description;
    private String category; // "Integration", "Lifecycle", "Workflow", "Event", "Messaging", "Custom"
    private boolean isExternalIo;
    private List<CommandParameterDto> parameters = new ArrayList<>();

    public CommandMetadataDto() {
    }

    public CommandMetadataDto(String type, String displayName, String description, String category, boolean isExternalIo) {
        this.type = type;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.isExternalIo = isExternalIo;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isExternalIo() { return isExternalIo; }
    public void setExternalIo(boolean externalIo) { isExternalIo = externalIo; }

    public List<CommandParameterDto> getParameters() { return parameters; }
    public void setParameters(List<CommandParameterDto> parameters) { this.parameters = parameters; }
}
