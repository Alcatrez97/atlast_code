package com.enterprise.atlas.workflow.service;

import com.enterprise.atlas.common.dto.RuleDto;
import com.enterprise.atlas.common.dto.ValidationResultDto;
import com.enterprise.atlas.workflow.entity.Rule;
import com.enterprise.atlas.workflow.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RuleService {

    @Autowired
    private RuleRepository ruleRepository;

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Transactional(readOnly = true)
    public List<RuleDto> getAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RuleDto> getRuleById(String id) {
        return ruleRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<RuleDto> getRuleByKey(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey).map(this::toDto);
    }

    public RuleDto createRule(RuleDto dto) {
        if (dto.getRuleKey() == null || dto.getRuleKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule key is required.");
        }
        if (ruleRepository.findByRuleKey(dto.getRuleKey()).isPresent()) {
            throw new IllegalArgumentException("Rule with key '" + dto.getRuleKey() + "' already exists.");
        }

        // Validate syntax
        ValidationResultDto validation = validateExpression(dto.getExpression());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid SpEL expression: " + String.join(", ", validation.getErrors()));
        }

        Rule rule = new Rule();
        rule.setId(UUID.randomUUID().toString());
        rule.setRuleKey(dto.getRuleKey().trim());
        rule.setName(dto.getName() != null ? dto.getName().trim() : dto.getRuleKey());
        rule.setDescription(dto.getDescription());
        rule.setExpression(dto.getExpression());
        rule.setActive(dto.isActive());

        rule = ruleRepository.save(rule);
        return toDto(rule);
    }

    public RuleDto updateRule(String id, RuleDto dto) {
        Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found with ID: " + id));

        if (dto.getRuleKey() == null || dto.getRuleKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule key is required.");
        }

        // Check key collision if key changed
        if (!rule.getRuleKey().equalsIgnoreCase(dto.getRuleKey().trim())) {
            Optional<Rule> collision = ruleRepository.findByRuleKey(dto.getRuleKey().trim());
            if (collision.isPresent()) {
                throw new IllegalArgumentException("Another rule with key '" + dto.getRuleKey() + "' already exists.");
            }
        }

        // Validate syntax
        ValidationResultDto validation = validateExpression(dto.getExpression());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid SpEL expression: " + String.join(", ", validation.getErrors()));
        }

        rule.setRuleKey(dto.getRuleKey().trim());
        rule.setName(dto.getName() != null ? dto.getName().trim() : dto.getRuleKey());
        rule.setDescription(dto.getDescription());
        rule.setExpression(dto.getExpression());
        rule.setActive(dto.isActive());
        rule.setUpdatedAt(LocalDateTime.now());

        rule = ruleRepository.save(rule);
        return toDto(rule);
    }

    public void deleteRule(String id) {
        Rule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found with ID: " + id));
        rule.setActive(false);
        ruleRepository.save(rule);
    }

    public ValidationResultDto validateExpression(String expression) {
        ValidationResultDto result = new ValidationResultDto();
        result.setValid(true);

        if (expression == null || expression.trim().isEmpty()) {
            result.addError("Expression cannot be empty.");
            return result;
        }

        try {
            parser.parseExpression(expression);
        } catch (Exception e) {
            result.addError("Syntax error: " + e.getMessage());
        }

        return result;
    }

    public Object evaluateExpression(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be empty.");
        }
        Map<String, Object> root = Map.of("context", context != null ? context : Map.of());
        StandardEvaluationContext spelCtx = new StandardEvaluationContext(root);
        spelCtx.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
        spelCtx.setVariable("context", context != null ? context : Map.of());
        return parser.parseExpression(expression).getValue(spelCtx);
    }

    private RuleDto toDto(Rule rule) {
        if (rule == null) return null;
        RuleDto dto = new RuleDto();
        dto.setId(rule.getId());
        dto.setRuleKey(rule.getRuleKey());
        dto.setName(rule.getName());
        dto.setDescription(rule.getDescription());
        dto.setExpression(rule.getExpression());
        dto.setActive(rule.isActive());
        dto.setCreatedAt(rule.getCreatedAt());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
