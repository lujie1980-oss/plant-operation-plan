/** 各实体 General 字段（固定列，不在 Catalog 中维护） */
export const MASTER_FIELD_GENERAL_REFERENCE: Record<
  string,
  { fieldKey: string; labelZh: string; dataType: string }[]
> = {
  MATERIAL: [
    { fieldKey: 'siteCode', labelZh: '基地代码', dataType: 'STRING' },
    { fieldKey: 'materialCode', labelZh: '产品代码', dataType: 'STRING' },
    { fieldKey: 'materialName', labelZh: '产品名称', dataType: 'STRING' },
    { fieldKey: 'uomCode', labelZh: '主计量单位', dataType: 'STRING' },
    { fieldKey: 'materialType', labelZh: '物料类型', dataType: 'STRING' },
  ],
  PRODUCT_RESOURCE: [
    { fieldKey: 'productCode', labelZh: '料号', dataType: 'STRING' },
    { fieldKey: 'sequenceNo', labelZh: '工序编号', dataType: 'INTEGER' },
    { fieldKey: 'resourcePriority', labelZh: '资源优先级', dataType: 'INTEGER' },
    { fieldKey: 'operationName', labelZh: '工序名称', dataType: 'STRING' },
    { fieldKey: 'resourceId', labelZh: '设备组', dataType: 'STRING' },
    { fieldKey: 'processTimeSeconds', labelZh: '制造CT(秒)', dataType: 'NUMBER' },
    { fieldKey: 'setupTimeMinutes', labelZh: '换型(分钟)', dataType: 'INTEGER' },
  ],
  SALES_ORDER: [
    { fieldKey: 'salesOrderNo', labelZh: '订单号', dataType: 'STRING' },
    { fieldKey: 'productCode', labelZh: '产品', dataType: 'STRING' },
    { fieldKey: 'orderQty', labelZh: '数量', dataType: 'NUMBER' },
    { fieldKey: 'dueDate', labelZh: '交期', dataType: 'DATE' },
    { fieldKey: 'priority', labelZh: '优先级', dataType: 'INTEGER' },
    { fieldKey: 'status', labelZh: '状态', dataType: 'STRING' },
  ],
};

export const MASTER_FIELD_ENTITY_OPTIONS = [
  { value: 'MATERIAL', label: '物料主数据' },
  { value: 'PRODUCT_RESOURCE', label: '产品工艺' },
  { value: 'SALES_ORDER', label: '销售订单' },
];

export const MASTER_FIELD_DATA_TYPE_OPTIONS = [
  { value: 'STRING', label: '文本' },
  { value: 'NUMBER', label: '小数' },
  { value: 'INTEGER', label: '整数' },
  { value: 'DATE', label: '日期' },
  { value: 'BOOL', label: '布尔' },
];
