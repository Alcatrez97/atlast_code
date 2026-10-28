package com.vi.atlas.workflow.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that stores boolean values as 'Y' / 'N' VARCHAR(1).
 * <p>
 * This replaces {@code org.hibernate.type.YesNoConverter} which maps to CHAR type.
 * This converter explicitly produces VARCHAR-compatible values, ensuring correct
 * behavior in Oracle DB (VARCHAR2(1)) and H2 (VARCHAR(1)).
 * </p>
 *
 * <pre>
 *   true  → 'Y'
 *   false → 'N'
 *   null  → null
 * </pre>
 *
 * Usage:
 * <pre>
 *   {@literal @}Convert(converter = YNBooleanConverter.class)
 *   {@literal @}Column(name = "is_active", nullable = false, length = 1)
 *   private boolean active = true;
 * </pre>
 */
@Converter
public class YNBooleanConverter implements AttributeConverter<Boolean, String> {

    private static final String YES = "Y";
    private static final String NO  = "N";

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) return null;
        return attribute ? YES : NO;
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return YES.equalsIgnoreCase(dbData.trim());
    }
}
