package com.plantops.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.CreateRuleSetVersionRequest;
import com.plantops.api.dto.RuleSetVersionDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.RuleSetVersionEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.rules.RuleSetSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RuleSetVersionService {

    @Inject
    ObjectMapper objectMapper;

    public List<RuleSetVersionDto> list() {
        ensureDefaults();
        return RuleSetVersionEntity.listInWorkspace().stream().map(this::toDto).toList();
    }

    public RuleSetVersionDto get(String ruleSetVersionId) {
        RuleSetVersionEntity e = findRequired(ruleSetVersionId);
        return toDto(e);
    }

    @Transactional
    public RuleSetVersionDto create(CreateRuleSetVersionRequest req) {
        ensureDefaults();
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("规则版本名称不能为空");
        }
        String name = req.name().trim();
        RuleSetVersionEntity source = null;
        if (req.copyFromRuleSetVersionId() != null && !req.copyFromRuleSetVersionId().isBlank()) {
            source = findRequired(req.copyFromRuleSetVersionId());
        } else {
            source = RuleSetVersionEntity.findDefault();
        }
        RuleSetVersionEntity row = new RuleSetVersionEntity();
        row.ruleSetVersionId = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        row.name = name;
        row.isDefault = false;
        row.snapshotJson = source != null && source.snapshotJson != null
                ? source.snapshotJson
                : serialize(captureFromWorkspace());
        row.createdAt = LocalDateTime.now();
        row.updatedAt = row.createdAt;
        row.stampWorkspace();
        row.persist();
        return toDto(row);
    }

    @Transactional
    public RuleSetVersionDto syncFromWorkspace(String ruleSetVersionId) {
        RuleSetVersionEntity row = findRequired(ruleSetVersionId);
        row.snapshotJson = serialize(captureFromWorkspace());
        row.updatedAt = LocalDateTime.now();
        row.persist();
        return toDto(row);
    }

    /** 将规则版本快照应用到当前工作区主数据（计划运行前调用）。 */
    @Transactional
    public void applyToWorkspace(String ruleSetVersionId) {
        RuleSetVersionEntity row = findRequired(ruleSetVersionId);
        if (row.isDefault) {
            return;
        }
        RuleSetSnapshot snapshot = deserialize(row.snapshotJson);
        if (snapshot == null) {
            return;
        }
        applyChangeovers(snapshot.changeovers());
        applyBomRules(snapshot.bomRules());
        applyDemandRules(snapshot.demandRules());
    }

    @Transactional
    public RuleSetVersionEntity ensureDefaults() {
        RuleSetVersionEntity existing = RuleSetVersionEntity.findDefault();
        if (existing != null) {
            if (existing.snapshotJson == null || existing.snapshotJson.isBlank()) {
                existing.snapshotJson = serialize(captureFromWorkspace());
                existing.updatedAt = LocalDateTime.now();
                existing.persist();
            }
            return existing;
        }
        RuleSetVersionEntity row = new RuleSetVersionEntity();
        row.ruleSetVersionId = "RSV-DEFAULT";
        row.name = "默认规则";
        row.isDefault = true;
        row.snapshotJson = serialize(captureFromWorkspace());
        row.createdAt = LocalDateTime.now();
        row.updatedAt = row.createdAt;
        row.stampWorkspace();
        row.persist();
        return row;
    }

    public RuleSetSnapshot captureFromWorkspace() {
        List<RuleSetSnapshot.ChangeoverRule> changeovers = ChangeoverMatrixEntity.listInWorkspace().stream()
                .map(c -> new RuleSetSnapshot.ChangeoverRule(
                        c.operationName,
                        c.attributeKey,
                        c.fromAttributeValue,
                        c.toAttributeValue,
                        c.setupMinutes))
                .toList();
        List<RuleSetSnapshot.BomRule> bomRules = BomComponentEntity.listInWorkspace().stream()
                .map(b -> new RuleSetSnapshot.BomRule(
                        b.finishedProductCode,
                        b.parentProductCode,
                        b.componentProductCode,
                        b.isCriticalComponent))
                .toList();
        List<RuleSetSnapshot.DemandRule> demandRules = SalesOrderLineEntity.listInWorkspace().stream()
                .map(s -> new RuleSetSnapshot.DemandRule(
                        s.salesOrderNo,
                        s.salesOrderLineNo,
                        s.priority,
                        s.expediteLevel,
                        s.scheduleLockFlag))
                .toList();
        return new RuleSetSnapshot(changeovers, bomRules, demandRules);
    }

    private void applyChangeovers(List<RuleSetSnapshot.ChangeoverRule> rules) {
        if (rules == null) {
            return;
        }
        for (RuleSetSnapshot.ChangeoverRule r : rules) {
            ChangeoverMatrixEntity e = ChangeoverMatrixEntity.findEntry(
                    r.operationName(), r.attributeKey(), r.fromAttributeValue(), r.toAttributeValue());
            if (e == null) {
                e = new ChangeoverMatrixEntity();
                e.operationName = r.operationName();
                e.attributeKey = r.attributeKey();
                e.fromAttributeValue = r.fromAttributeValue();
                e.toAttributeValue = r.toAttributeValue();
                e.ensureWorkspace();
                e.persist();
            }
            e.setupMinutes = r.setupMinutes();
        }
    }

    private void applyBomRules(List<RuleSetSnapshot.BomRule> rules) {
        if (rules == null) {
            return;
        }
        for (RuleSetSnapshot.BomRule r : rules) {
            BomComponentEntity e = BomComponentEntity.find(
                    "workspaceId = ?1 and finishedProductCode = ?2 and parentProductCode = ?3 and componentProductCode = ?4",
                    BomComponentEntity.ws(),
                    r.finishedProductCode(),
                    r.parentProductCode(),
                    r.componentProductCode())
                    .firstResult();
            if (e != null) {
                e.isCriticalComponent = r.isCriticalComponent();
                e.persist();
            }
        }
    }

    private void applyDemandRules(List<RuleSetSnapshot.DemandRule> rules) {
        if (rules == null) {
            return;
        }
        for (RuleSetSnapshot.DemandRule r : rules) {
            SalesOrderLineEntity e = SalesOrderLineEntity.findByKey(r.salesOrderNo(), r.salesOrderLineNo());
            if (e != null) {
                e.priority = r.priority();
                e.expediteLevel = r.expediteLevel();
                e.scheduleLockFlag = r.scheduleLockFlag();
                e.persist();
            }
        }
    }

    private RuleSetVersionEntity findRequired(String id) {
        RuleSetVersionEntity e = RuleSetVersionEntity.findById(id);
        if (e == null) {
            throw new NotFoundException("规则版本不存在: " + id);
        }
        return e;
    }

    private String serialize(RuleSetSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("规则快照序列化失败", e);
        }
    }

    private RuleSetSnapshot deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RuleSetSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("规则快照解析失败", e);
        }
    }

    private RuleSetVersionDto toDto(RuleSetVersionEntity e) {
        return new RuleSetVersionDto(
                e.ruleSetVersionId,
                e.name,
                e.isDefault,
                e.createdAt != null ? e.createdAt.toString() : null,
                e.updatedAt != null ? e.updatedAt.toString() : null);
    }
}
