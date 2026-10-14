package ru.pacco.compliance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceRulesRepository extends JpaRepository<ComplianceRulesEntity, Integer> {
}
