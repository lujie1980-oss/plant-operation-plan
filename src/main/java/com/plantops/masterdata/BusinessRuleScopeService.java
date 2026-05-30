package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataDtos.BusinessRuleScopeDto;
import com.plantops.persistence.entity.BusinessRuleScopeEntity;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;
import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BusinessRuleScopeService {

    @Inject
    EntityManager em;

    /** V28 迁移前已启动的库可能缺 Panache 序列，启动时幂等补建。 */
    @Transactional
    void ensurePanacheSequence(@Observes StartupEvent event) {
        em.createNativeQuery(
                "CREATE SEQUENCE IF NOT EXISTS business_rule_scope_SEQ START WITH 1 INCREMENT BY 50")
                .executeUpdate();
    }

    public boolean isMasterPlanEnabled(String ruleTypeId) {
        return findOrDefault(ruleTypeId).enableMasterPlan;
    }

    public boolean isDetailScheduleEnabled(String ruleTypeId) {
        return findOrDefault(ruleTypeId).enableDetailSchedule;
    }

    public List<BusinessRuleScopeDto> listAll() {
        ensureDefaults();
        return BusinessRuleScopeEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing(e -> BusinessRuleTypeIds.ALL.indexOf(e.ruleTypeId)))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BusinessRuleScopeDto upsert(BusinessRuleScopeDto dto) {
        if (dto.ruleTypeId() == null || dto.ruleTypeId().isBlank()) {
            throw new IllegalArgumentException("规则项目 ID 不能为空");
        }
        if (!BusinessRuleTypeIds.ALL.contains(dto.ruleTypeId())) {
            throw new IllegalArgumentException("未知规则项目: " + dto.ruleTypeId());
        }
        BusinessRuleScopeEntity e = BusinessRuleScopeEntity.findByRuleType(dto.ruleTypeId());
        if (e == null) {
            e = new BusinessRuleScopeEntity();
            e.ruleTypeId = dto.ruleTypeId();
            e.ensureWorkspace();
            e.persist();
        }
        e.enableMasterPlan = dto.enableMasterPlan();
        e.enableDetailSchedule = dto.enableDetailSchedule();
        return toDto(e);
    }

    public ChangeoverRuleIndex loadChangeoverIndex() {
        if (!isDetailScheduleEnabled(BusinessRuleTypeIds.CHANGEOVER)) {
            return new ChangeoverRuleIndex(List.of());
        }
        List<ChangeoverRuleIndex.Rule> rules = ChangeoverMatrixEntity.listInWorkspace().stream()
                .map(e -> new ChangeoverRuleIndex.Rule(
                        e.operationName,
                        e.attributeKey,
                        e.fromAttributeValue,
                        e.toAttributeValue,
                        e.setupMinutes))
                .toList();
        return new ChangeoverRuleIndex(rules);
    }

    public OperationTransferTimeIndex loadTransferTimeIndex() {
        if (!isMasterPlanEnabled(BusinessRuleTypeIds.OPERATION_TRANSFER_TIME)) {
            return new OperationTransferTimeIndex(List.of());
        }
        List<OperationTransferTimeIndex.Rule> rules = OperationTransferTimeRuleEntity.listInWorkspace().stream()
                .map(e -> new OperationTransferTimeIndex.Rule(
                        e.productCode,
                        e.fromOperationName,
                        e.toOperationName,
                        e.transferMinutes,
                        e.minTransferMinutes))
                .toList();
        return new OperationTransferTimeIndex(rules);
    }

    @Transactional
    void ensureDefaults() {
        Map<String, BusinessRuleScopeEntity> existing = BusinessRuleScopeEntity.listInWorkspace().stream()
                .collect(Collectors.toMap(e -> e.ruleTypeId, e -> e, (a, b) -> a));
        List<BusinessRuleScopeEntity> toPersist = new ArrayList<>();
        for (String ruleTypeId : BusinessRuleTypeIds.ALL) {
            if (!existing.containsKey(ruleTypeId)) {
                BusinessRuleScopeEntity row = new BusinessRuleScopeEntity();
                row.ruleTypeId = ruleTypeId;
                row.enableMasterPlan = true;
                row.enableDetailSchedule = true;
                row.ensureWorkspace();
                toPersist.add(row);
            }
        }
        for (BusinessRuleScopeEntity row : toPersist) {
            row.persist();
        }
    }

    private BusinessRuleScopeEntity findOrDefault(String ruleTypeId) {
        BusinessRuleScopeEntity e = BusinessRuleScopeEntity.findByRuleType(ruleTypeId);
        if (e != null) {
            return e;
        }
        BusinessRuleScopeEntity fallback = new BusinessRuleScopeEntity();
        fallback.ruleTypeId = ruleTypeId;
        fallback.enableMasterPlan = true;
        fallback.enableDetailSchedule = true;
        return fallback;
    }

    private BusinessRuleScopeDto toDto(BusinessRuleScopeEntity e) {
        return new BusinessRuleScopeDto(
                e.ruleTypeId,
                BusinessRuleTypeIds.labelOf(e.ruleTypeId),
                e.enableMasterPlan,
                e.enableDetailSchedule);
    }
}
