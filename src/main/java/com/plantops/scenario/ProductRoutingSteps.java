package com.plantops.scenario;



import com.plantops.persistence.entity.ProductResourceEntity;



import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;



/**

 * 产品工艺路线：同一 {@code sequenceNo} 下多行 {@code product_resource} 表示该工序可在多台资源上加工，

 * 按 {@code resourcePriority}（数值越小越优先）排序；求解时优先占用高优先级资源，必要时可落到备选资源。

 */

public final class ProductRoutingSteps {



    public record ResourceOption(

            String resourceId,

            Integer resourcePriority,

            BigDecimal processTimeSeconds,

            int setupTimeMinutes) {

    }



    public record Operation(int sequenceNo, String operationName, List<ResourceOption> resourceOptions) {

        public String primaryResourceId() {

            return resourceOptions.isEmpty() ? null : resourceOptions.get(0).resourceId();

        }



        public List<String> allowedResourceIds() {

            return resourceOptions.stream().map(ResourceOption::resourceId).toList();

        }



        public BigDecimal primaryProcessTimeSeconds() {

            return resourceOptions.isEmpty() ? null : resourceOptions.get(0).processTimeSeconds();

        }

    }



    /** 兼容旧调用：每道工序一行，取最高优先级资源。 */

    public record Step(int sequenceNo, String operationName, String resourceId, BigDecimal processTimeSeconds) {

    }



    private ProductRoutingSteps() {

    }



    public static List<Operation> operationsForProduct(String productCode) {

        List<ProductResourceEntity> rows = ProductResourceEntity.findByProductOrdered(productCode);

        if (rows.isEmpty()) {

            List<ProductRoutingCatalog.RoutingStep> fallback = ProductRoutingCatalog.stepsFor(productCode);

            List<Operation> out = new ArrayList<>(fallback.size());

            for (int i = 0; i < fallback.size(); i++) {

                ProductRoutingCatalog.RoutingStep s = fallback.get(i);

                out.add(new Operation(

                        i + 1,

                        s.operationName(),

                        List.of(new ResourceOption(s.resourceId(), 1, null, 0))));

            }

            return out;

        }

        Map<Integer, List<ProductResourceEntity>> bySequence = new LinkedHashMap<>();

        int fallbackSeq = 1;

        for (ProductResourceEntity row : rows) {

            int seq = row.sequenceNo != null ? row.sequenceNo : fallbackSeq++;

            bySequence.computeIfAbsent(seq, k -> new ArrayList<>()).add(row);

        }

        List<Operation> out = new ArrayList<>(bySequence.size());

        for (Map.Entry<Integer, List<ProductResourceEntity>> entry : bySequence.entrySet()) {

            List<ProductResourceEntity> group = new ArrayList<>(entry.getValue());

            group.sort(Comparator

                    .comparing((ProductResourceEntity r) -> r.resourcePriority != null
                            ? r.resourcePriority
                            : ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY)

                    .thenComparing(r -> r.id != null ? r.id : Long.MAX_VALUE));

            List<ResourceOption> options = new ArrayList<>(group.size());

            for (ProductResourceEntity row : group) {

                options.add(new ResourceOption(

                        row.resourceId,

                        row.resourcePriority,

                        row.processTimeSeconds,

                        row.setupTimeMinutes));

            }

            ProductResourceEntity head = group.get(0);

            String name = head.operationName != null && !head.operationName.isBlank()

                    ? head.operationName

                    : "工序 " + entry.getKey();

            out.add(new Operation(entry.getKey(), name, List.copyOf(options)));

        }

        return out;

    }



    public static List<Step> forProduct(String productCode) {

        return operationsForProduct(productCode).stream()

                .map(op -> new Step(

                        op.sequenceNo(),

                        op.operationName(),

                        op.primaryResourceId(),

                        op.primaryProcessTimeSeconds()))

                .toList();

    }



    /**

     * 工单总工时（分钟）：按工序（非按资源行）累加「换型 + 数量×制造CT」，每道工序只计一次（取最高优先级资源）。

     */

    public static int totalDurationMinutes(String productCode, BigDecimal quantity) {

        if (productCode == null || productCode.isBlank()) {

            return 60;

        }

        List<Operation> operations = operationsForProduct(productCode);

        if (!operations.isEmpty()) {

            int total = 0;

            for (Operation op : operations) {

                ResourceOption primary = op.resourceOptions().get(0);

                total += operationDurationMinutes(primary.setupTimeMinutes(), primary.processTimeSeconds(), quantity);

            }

            return Math.max(1, total);

        }

        List<Step> steps = forProduct(productCode);

        if (steps.isEmpty()) {

            return 60;

        }

        return Math.max(1, steps.size() * 15);

    }



    public static int durationMinutesForOperation(Operation operation, BigDecimal quantity) {

        if (operation == null || operation.resourceOptions().isEmpty()) {

            return 15;

        }

        ResourceOption primary = operation.resourceOptions().get(0);

        return operationDurationMinutes(primary.setupTimeMinutes(), primary.processTimeSeconds(), quantity);

    }



    /** 单道工序在指定机台上的工时（分钟），用于按资源统计负荷。 */

    public static int durationMinutesForResource(String productCode, String resourceId, BigDecimal quantity) {

        if (productCode == null || productCode.isBlank() || resourceId == null || resourceId.isBlank()) {

            return 0;

        }

        ProductResourceEntity row = ProductResourceEntity.findByProductAndResource(productCode, resourceId);

        if (row == null) {

            return 0;

        }

        return operationDurationMinutes(row.setupTimeMinutes, row.processTimeSeconds, quantity);

    }



    static int operationDurationMinutes(int setupMinutes, BigDecimal processTimeSeconds, BigDecimal quantity) {

        int total = Math.max(0, setupMinutes);

        if (processTimeSeconds != null

                && processTimeSeconds.compareTo(BigDecimal.ZERO) > 0

                && quantity != null) {

            total += quantity

                    .multiply(processTimeSeconds)

                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.UP)

                    .intValue();

            return Math.max(1, total);

        }

        return Math.max(15, total + 15);

    }



    public static int durationMinutesForStep(

            Step step,

            List<Step> allSteps,

            BigDecimal quantity,

            String fallbackResourceId,

            int fallbackTotalMinutes) {

        if (step.processTimeSeconds() != null

                && step.processTimeSeconds().compareTo(BigDecimal.ZERO) > 0

                && quantity != null) {

            return Math.max(

                    1,

                    quantity

                            .multiply(step.processTimeSeconds())

                            .divide(BigDecimal.valueOf(60), 0, RoundingMode.UP)

                            .intValue());

        }

        long totalSeconds = 0;

        for (Step s : allSteps) {

            if (s.processTimeSeconds() != null) {

                totalSeconds += s.processTimeSeconds().longValue();

            }

        }

        if (totalSeconds > 0 && step.processTimeSeconds() != null) {

            long share = step.processTimeSeconds().longValue();

            return Math.max(15, (int) Math.round((double) fallbackTotalMinutes * share / totalSeconds));

        }

        return Math.max(15, fallbackTotalMinutes / Math.max(1, allSteps.size()));

    }

}


