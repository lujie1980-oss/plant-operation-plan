package com.plantops.solver.masterplan;

/**
 * 主计划 BOM 先后：子件工单（child）须先于父件工单（parent）完工。
 */
public record BomDependencyEdge(String parentWorkOrderNo, String childWorkOrderNo) {
}
