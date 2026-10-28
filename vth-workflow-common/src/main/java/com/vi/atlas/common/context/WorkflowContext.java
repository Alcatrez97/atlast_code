package com.vi.atlas.common.context;

import com.vi.atlas.common.dto.ExecutionRequestDto;

import java.io.Serializable;
import java.util.*;

/**
 * Canonical context accessor and normalizer for workflow execution.
 * <p>
 * Ensures critical domain identifiers (circleId, cafId, businessKey, formId, contextId)
 * are parsed and normalized in-place with standard camelCase keys, preventing repetitive
 * defensive alias lookups across controllers, listeners, traversal engines, and node executors.
 */
public class WorkflowContext implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String KEY_CIRCLE_ID = "circleId";
    public static final String KEY_CAF_ID = "cafId";
    public static final String KEY_BUSINESS_KEY = "businessKey";
    public static final String KEY_FORM_ID = "formId";
    public static final String KEY_CONTEXT_ID = "contextId";
    public static final String KEY_ENT_TYPE_ID = "entTypeId";

    public static final String[] CIRCLE_ID_ALIASES = {
            "circleId", "circle_id", "circleid", "CircleId", "CIRCLE_ID",
            "circle-id", "circleID", "CircleID", "CIRCLEID"
    };

    public static final String[] CAF_ID_ALIASES = {
            "cafId", "caf_id", "cafid", "CafId", "CAF_ID", "cafID", "CAFID", "id", "customerFormId"
    };

    public static final String[] BUSINESS_KEY_ALIASES = {
            "businessKey", "business_key", "businesskey", "BusinessKey", "BUSINESS_KEY"
    };

    public static final String[] FORM_ID_ALIASES = {
            "formId", "form_id", "formid", "FormId", "FORM_ID", "customerFormId"
    };

    public static final String[] CONTEXT_ID_ALIASES = {
            "contextId", "context_id", "trackingId", "transactionId", "orderId"
    };

    public static final String[] ENT_TYPE_ID_ALIASES = {
            "entTypeId", "ent_type_id", "entityTypeId", "entity_type_id"
    };

    private final Map<String, Object> map;

    private WorkflowContext(Map<String, Object> underlyingMap) {
        this.map = underlyingMap != null ? underlyingMap : new HashMap<>();
        normalize();
    }

    /**
     * Creates a WorkflowContext wrapping the provided map and normalizes canonical keys in-place.
     */
    public static WorkflowContext from(Map<String, Object> map) {
        return new WorkflowContext(map);
    }

    /**
     * Creates a new, empty WorkflowContext.
     */
    public static WorkflowContext empty() {
        return new WorkflowContext(new HashMap<>());
    }

    /**
     * Creates and normalizes a WorkflowContext from an incoming {@link ExecutionRequestDto}.
     */
    public static WorkflowContext of(ExecutionRequestDto request) {
        if (request == null) {
            return empty();
        }
        Map<String, Object> ctx = request.getContext() != null
                ? new HashMap<>(request.getContext())
                : new HashMap<>();

        if (request.getCircleId() != null) {
            ctx.putIfAbsent(KEY_CIRCLE_ID, request.getCircleId());
        }
        if (request.getBusinessKey() != null && !request.getBusinessKey().isBlank()) {
            ctx.putIfAbsent(KEY_BUSINESS_KEY, request.getBusinessKey());
        }
        if (request.getCafId() != null) {
            ctx.putIfAbsent(KEY_CAF_ID, request.getCafId());
        }

        return from(ctx);
    }

    /**
     * Normalizes all canonical identity keys in-place so SpEL, DB JSON, and downstream consumers
     * find standardized keys without dropping existing legacy aliases.
     */
    public WorkflowContext normalize() {
        // 1. Circle ID
        Integer resolvedCircleId = resolveInteger(CIRCLE_ID_ALIASES);
        if (resolvedCircleId != null) {
            map.put(KEY_CIRCLE_ID, resolvedCircleId);
        }

        // 2. CAF ID (strictly numeric)
        Long resolvedCafId = resolveStrictNumeric(CAF_ID_ALIASES);
        if (resolvedCafId != null) {
            map.put(KEY_CAF_ID, resolvedCafId);
        }

        // 3. Form ID
        String resolvedFormId = resolveString(FORM_ID_ALIASES);
        if (resolvedFormId == null && resolvedCafId != null) {
            resolvedFormId = String.valueOf(resolvedCafId);
        }
        if (resolvedFormId != null) {
            map.put(KEY_FORM_ID, resolvedFormId);
        }

        // 4. Business Key
        String resolvedBusinessKey = resolveString(BUSINESS_KEY_ALIASES);
        if (resolvedBusinessKey == null && resolvedFormId != null) {
            resolvedBusinessKey = resolvedFormId;
        } else if (resolvedBusinessKey == null && resolvedCafId != null) {
            resolvedBusinessKey = String.valueOf(resolvedCafId);
        }
        if (resolvedBusinessKey != null) {
            map.put(KEY_BUSINESS_KEY, resolvedBusinessKey);
        }

        // 5. Context ID
        String resolvedContextId = resolveString(CONTEXT_ID_ALIASES);
        if (resolvedContextId == null && resolvedBusinessKey != null) {
            resolvedContextId = resolvedBusinessKey;
        }
        if (resolvedContextId != null) {
            map.put(KEY_CONTEXT_ID, resolvedContextId);
        }

        // 6. Entity Type ID
        String resolvedEntTypeId = resolveString(ENT_TYPE_ID_ALIASES);
        if (resolvedEntTypeId != null) {
            map.put(KEY_ENT_TYPE_ID, resolvedEntTypeId);
        }

        return this;
    }

    public Integer getCircleId() {
        return resolveInteger(CIRCLE_ID_ALIASES);
    }

    public Integer getCircleId(Integer fallback) {
        Integer val = getCircleId();
        return val != null ? val : fallback;
    }

    public void setCircleId(Integer circleId) {
        if (circleId != null) {
            map.put(KEY_CIRCLE_ID, circleId);
        } else {
            map.remove(KEY_CIRCLE_ID);
        }
    }

    public Long getCafId() {
        return resolveStrictNumeric(CAF_ID_ALIASES);
    }

    public String getCafIdAsString() {
        Long cid = getCafId();
        return cid != null ? String.valueOf(cid) : null;
    }

    public void setCafId(Long cafId) {
        if (cafId != null) {
            map.put(KEY_CAF_ID, cafId);
        } else {
            map.remove(KEY_CAF_ID);
        }
    }

    public String getBusinessKey() {
        String bk = resolveString(BUSINESS_KEY_ALIASES);
        if (bk != null && !bk.isBlank()) {
            return bk;
        }
        String fid = getFormId();
        if (fid != null && !fid.isBlank()) {
            return fid;
        }
        return getCafIdAsString();
    }

    public void setBusinessKey(String businessKey) {
        if (businessKey != null) {
            map.put(KEY_BUSINESS_KEY, businessKey);
        } else {
            map.remove(KEY_BUSINESS_KEY);
        }
    }

    public String getFormId() {
        String fid = resolveString(FORM_ID_ALIASES);
        if (fid != null && !fid.isBlank()) {
            return fid;
        }
        return getCafIdAsString();
    }

    public void setFormId(String formId) {
        if (formId != null) {
            map.put(KEY_FORM_ID, formId);
        } else {
            map.remove(KEY_FORM_ID);
        }
    }

    public String getContextId() {
        String cid = resolveString(CONTEXT_ID_ALIASES);
        if (cid != null && !cid.isBlank()) {
            return cid;
        }
        return getBusinessKey();
    }

    public void setContextId(String contextId) {
        if (contextId != null) {
            map.put(KEY_CONTEXT_ID, contextId);
        } else {
            map.remove(KEY_CONTEXT_ID);
        }
    }

    public String getEntTypeId(String defaultEntType) {
        String val = resolveString(ENT_TYPE_ID_ALIASES);
        return (val != null && !val.isBlank()) ? val : defaultEntType;
    }

    public void setEntTypeId(String entTypeId) {
        if (entTypeId != null) {
            map.put(KEY_ENT_TYPE_ID, entTypeId);
        } else {
            map.remove(KEY_ENT_TYPE_ID);
        }
    }

    public Map<String, Object> asMap() {
        return map;
    }

    public Object get(String key) {
        return map.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object val = map.get(key);
        if (val == null) return null;
        if (type.isInstance(val)) {
            return (T) val;
        }
        if (type == String.class) {
            return (T) String.valueOf(val);
        }
        if (type == Integer.class) {
            Long num = parseStrictNumeric(val);
            return num != null ? (T) Integer.valueOf(num.intValue()) : null;
        }
        if (type == Long.class) {
            Long num = parseStrictNumeric(val);
            return num != null ? (T) num : null;
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(String.valueOf(val));
        }
        return null;
    }

    public void put(String key, Object value) {
        map.put(key, value);
    }

    public void putIfAbsent(String key, Object value) {
        map.putIfAbsent(key, value);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    // -------------------------------------------------------------------------
    // Resolution helpers
    // -------------------------------------------------------------------------

    private Integer resolveInteger(String[] keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Long parsed = parseStrictNumeric(map.get(k));
                if (parsed != null) {
                    return parsed.intValue();
                }
            }
        }
        return null;
    }

    private Long resolveStrictNumeric(String[] keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                Long parsed = parseStrictNumeric(map.get(k));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private String resolveString(String[] keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                String val = String.valueOf(map.get(k)).trim();
                if (!val.isBlank() && !"null".equalsIgnoreCase(val)) {
                    return val;
                }
            }
        }
        return null;
    }

    /**
     * Parses an object strictly into a Long if numeric. Does NOT use hashcode fallback.
     */
    public static Long parseStrictNumeric(Object val) {
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        String str = val.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
