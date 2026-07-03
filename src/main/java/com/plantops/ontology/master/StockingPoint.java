package com.plantops.ontology.master;

import com.plantops.ontology.OntologyIds;

import java.util.List;

/**
 * 主计划四级库存点：原料 → 一阶半成品 → 二阶半成品 → 总成。
 */
public class StockingPoint {

    /** @deprecated 兼容旧装载路径，等价于 {@link #FG} */
    public static final String DEFAULT_FG = OntologyIds.DEFAULT_FG;

    public static final String RAW = "RAW";
    public static final String SFG_A = "SFG-A";
    public static final String SFG_B = "SFG-B";
    public static final String FG = "FG";

    private String id;
    private String stockingPointCode;
    private String displayName;

    public StockingPoint() {
    }

    public StockingPoint(String id, String stockingPointCode, String displayName) {
        this.id = id;
        this.stockingPointCode = stockingPointCode;
        this.displayName = displayName;
    }

    public static StockingPoint raw() {
        return new StockingPoint(RAW, RAW, "原料");
    }

    public static StockingPoint sfgA() {
        return new StockingPoint(SFG_A, SFG_A, "一阶 (SFG-A)");
    }

    public static StockingPoint sfgB() {
        return new StockingPoint(SFG_B, SFG_B, "二阶 (SFG-B)");
    }

    public static StockingPoint fg() {
        return new StockingPoint(FG, FG, "总成 (FG)");
    }

    /** @deprecated 使用 {@link #fg()} */
    public static StockingPoint defaultFg() {
        return fg();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStockingPointCode() {
        return stockingPointCode;
    }

    public void setStockingPointCode(String stockingPointCode) {
        this.stockingPointCode = stockingPointCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public static List<String> knownIds() {
        return List.of(RAW, SFG_A, SFG_B, FG);
    }
}
