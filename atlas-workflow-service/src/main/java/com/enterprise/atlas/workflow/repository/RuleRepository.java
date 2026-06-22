package com.enterprise.atlas.workflow.repository;

import com.enterprise.atlas.workflow.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RuleRepository extends JpaRepository<Rule, String> {
    Optional<Rule> findByRuleKey(String ruleKey);
}
