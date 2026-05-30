-- BOM: 成品料号、生效/失效、组件损耗率
ALTER TABLE bom_component ADD COLUMN finished_product_code VARCHAR(64);
ALTER TABLE bom_component ADD COLUMN bom_effective_from DATE;
ALTER TABLE bom_component ADD COLUMN bom_effective_to DATE;
ALTER TABLE bom_component ADD COLUMN component_effective_from DATE;
ALTER TABLE bom_component ADD COLUMN component_effective_to DATE;
ALTER TABLE bom_component ADD COLUMN scrap_rate DECIMAL(18,6);

-- 工艺路线：排程分组与标准人力
ALTER TABLE product_resource ADD COLUMN bom_level VARCHAR(32);
ALTER TABLE product_resource ADD COLUMN wire_material VARCHAR(128);
ALTER TABLE product_resource ADD COLUMN key_material VARCHAR(128);
ALTER TABLE product_resource ADD COLUMN male_female_end VARCHAR(64);
ALTER TABLE product_resource ADD COLUMN total_branch VARCHAR(128);
ALTER TABLE product_resource ADD COLUMN standard_labor DECIMAL(18,4);
