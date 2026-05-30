package com.plantops.scenario;

import java.util.List;

/**
 * 演示用工序路由：按产品类型展开工单下的工序与设备。
 */
public final class ProductRoutingCatalog {

    private ProductRoutingCatalog() {
    }

    public record RoutingStep(String operationName, String resourceId) {
    }

    public static List<RoutingStep> stepsFor(String productCode) {
        if (productCode == null) {
            return List.of(new RoutingStep("加工", "组装1线"));
        }
        if (productCode.contains("电子")) {
            return List.of(
                    new RoutingStep("SMT贴片", "SMT1"),
                    new RoutingStep("DIP插件", "DIP线"),
                    new RoutingStep("波峰焊", "波峰焊"),
                    new RoutingStep("功能测试", "测试1线"));
        }
        if (productCode.contains("机加")) {
            return List.of(
                    new RoutingStep("切割下料", "切割机"),
                    new RoutingStep("数控加工", "数车"),
                    new RoutingStep("铣削", "铣床"),
                    new RoutingStep("热处理", "热处理炉"));
        }
        if (productCode.contains("成品")) {
            return List.of(
                    new RoutingStep("组装", "组装1线"),
                    new RoutingStep("老化", "老化设备1"),
                    new RoutingStep("测试", "测试1线"),
                    new RoutingStep("包装", "包装线"));
        }
        return List.of(new RoutingStep("加工", "组装1线"));
    }
}
