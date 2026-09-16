package com.vi.atlas.workflow.dto;

import java.util.List;

public class CommandParameterDto {

    private String name;
    private String label;
    private String type; // "text", "number", "select", "multiselect", "json", "boolean"
    private boolean required;
    private Object defaultValue;
    private List<Option> options;
    private String dataSource; // "INTEGRATIONS", "BUCKETS", "WORKFLOWS", "EVENTS", "CONTEXT_SCHEMA"
    private String placeholder;
    private String description;

    public CommandParameterDto() {
    }

    public CommandParameterDto(String name, String label, String type, boolean required, Object defaultValue, String description) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public static class Option {
        private String label;
        private String value;

        public Option() {}

        public Option(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

    public List<Option> getOptions() { return options; }
    public void setOptions(List<Option> options) { this.options = options; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
