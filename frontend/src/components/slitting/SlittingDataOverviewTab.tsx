import { useEffect, useMemo, useState } from 'react';

import { BomHierarchyTree } from '../BomHierarchyTree';
import type { BomMd, InventoryMd, MaterialMd, ProductResourceMd, SalesOrderMd } from '../../types/masterData';
import type { MasterRoll } from '../../types/slitting';
import {
  buildFinishedProductBomRoot,
  collectBomMaterialCodes,
  filterBomsToMaterialMaster,
  listFinishedProducts,
} from '../../utils/bomTree';
import { MaterialCatalog } from '../../utils/materialCatalog';

type Props = {
  materials: MaterialMd[];
  boms: BomMd[];
  salesOrders: SalesOrderMd[];
  inventory: InventoryMd[];
  masterRolls: MasterRoll[];
  productResources: ProductResourceMd[];
};

function resolvePrimaryFinishedCode(codes: string[]): string {
  return codes.find((c) => c === 'M69/305*600M/1R/深黄') ?? codes[0] ?? '';
}

export function SlittingDataOverviewTab({
  materials,
  boms,
  salesOrders,
  inventory,
  masterRolls,
  productResources,
}: Props) {
  const catalog = useMemo(() => new MaterialCatalog(materials), [materials]);

  const finishedProducts = useMemo(
    () =>
      listFinishedProducts(boms, materials, productResources).filter(
        (row) => row.hasBom && catalog.has(row.materialCode),
      ),
    [boms, materials, productResources, catalog],
  );

  const [selectedFinishedCode, setSelectedFinishedCode] = useState('');
  const [selectedBomCode, setSelectedBomCode] = useState<string | null>(null);

  useEffect(() => {
    if (finishedProducts.length === 0) {
      setSelectedFinishedCode('');
      setSelectedBomCode(null);
      return;
    }
    if (!selectedFinishedCode || !finishedProducts.some((p) => p.materialCode === selectedFinishedCode)) {
      const next = resolvePrimaryFinishedCode(finishedProducts.map((p) => p.materialCode));
      setSelectedFinishedCode(next);
      setSelectedBomCode(next);
    }
  }, [finishedProducts, selectedFinishedCode]);

  const filteredBoms = useMemo(
    () => filterBomsToMaterialMaster(boms, catalog),
    [boms, catalog],
  );

  const bomRoot = useMemo(() => {
    if (!selectedFinishedCode) {
      return null;
    }
    return buildFinishedProductBomRoot(selectedFinishedCode, filteredBoms, materials);
  }, [selectedFinishedCode, filteredBoms, materials]);

  const relatedOrderCodes = useMemo(() => {
    if (!selectedFinishedCode) {
      return new Set<string>();
    }
    const codes = collectBomMaterialCodes(filteredBoms, selectedFinishedCode);
    codes.add(selectedFinishedCode);
    return codes;
  }, [selectedFinishedCode, filteredBoms]);

  const relatedSalesOrders = useMemo(
    () => salesOrders.filter((o) => relatedOrderCodes.has(o.productCode)),
    [salesOrders, relatedOrderCodes],
  );

  const selectedInventory = useMemo(() => {
    if (!selectedBomCode) {
      return [];
    }
    return inventory.filter((row) => row.productCode === selectedBomCode);
  }, [inventory, selectedBomCode]);

  const selectedMasterRolls = useMemo(() => {
    if (!selectedBomCode) {
      return [];
    }
    return masterRolls.filter(
      (roll) =>
        roll.productCode === selectedBomCode
        || roll.materialCode === selectedBomCode
        || roll.finishedProductCode === selectedBomCode,
    );
  }, [masterRolls, selectedBomCode]);

  const selectedMaterial = selectedBomCode ? catalog.get(selectedBomCode) : undefined;

  return (
    <section className="card slitting-overview">
      <div className="slitting-overview-grid">
        <div className="slitting-overview-panel">
          <h3 className="slitting-panel-title">成品物料</h3>
          <div className="slitting-overview-scroll">
            <table className="slitting-data-table slitting-overview-table">
              <thead>
                <tr>
                  <th>物料名称</th>
                  <th>规格描述</th>
                </tr>
              </thead>
              <tbody>
                {finishedProducts.map((row) => (
                  <tr
                    key={row.materialCode}
                    className={
                      selectedFinishedCode === row.materialCode ? 'slitting-overview-row is-selected' : 'slitting-overview-row'
                    }
                    onClick={() => {
                      setSelectedFinishedCode(row.materialCode);
                      setSelectedBomCode(row.materialCode);
                    }}
                  >
                    <td>{row.materialName ?? catalog.materialName(row.materialCode)}</td>
                    <td className="mono">{row.materialCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="slitting-overview-panel">
          <h3 className="slitting-panel-title">销售订单</h3>
          <p className="slitting-panel-hint">
            {selectedFinishedCode
              ? `与「${catalog.materialName(selectedFinishedCode)}」BOM 相关的订单行`
              : '请选择成品物料'}
          </p>
          <div className="slitting-overview-scroll">
            <table className="slitting-data-table slitting-overview-table">
              <thead>
                <tr>
                  <th>订单</th>
                  <th>物料名称</th>
                  <th>数量</th>
                  <th>交期</th>
                </tr>
              </thead>
              <tbody>
                {relatedSalesOrders.map((o) => (
                  <tr key={`${o.salesOrderNo}-${o.salesOrderLineNo}`}>
                    <td>
                      {o.salesOrderNo}-{o.salesOrderLineNo}
                    </td>
                    <td>{catalog.materialName(o.productCode)}</td>
                    <td>{o.orderQty}</td>
                    <td>{o.promiseDate ?? o.dueDate}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {relatedSalesOrders.length === 0 ? (
              <p className="slitting-props-empty">暂无相关销售订单</p>
            ) : null}
          </div>
        </div>

        <div className="slitting-overview-panel">
          <h3 className="slitting-panel-title">BOM 层级</h3>
          <p className="slitting-panel-hint">从成品到原料的多阶 BOM；点击节点查看库存</p>
          <div className="slitting-overview-scroll slitting-overview-bom-tree">
            {bomRoot ? (
              <BomHierarchyTree
                root={bomRoot}
                selectedProductCode={selectedBomCode}
                onSelect={setSelectedBomCode}
                compact
                initialExpandDepth={-1}
              />
            ) : (
              <p className="slitting-props-empty">暂无 BOM 结构</p>
            )}
          </div>
        </div>

        <div className="slitting-overview-panel">
          <h3 className="slitting-panel-title">库存</h3>
          <p className="slitting-panel-hint">
            选中：{selectedMaterial ? catalog.materialName(selectedBomCode) : '—'}
            {selectedBomCode ? `（${selectedBomCode}）` : ''}
          </p>
          <div className="slitting-overview-scroll">
            {selectedInventory.length > 0 ? (
              <table className="slitting-data-table slitting-overview-table">
                <thead>
                  <tr>
                    <th>库位</th>
                    <th>现有量</th>
                    <th>预留</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedInventory.map((row) => (
                    <tr key={`${row.stockingPointCode}-${row.productCode}-${row.id ?? ''}`}>
                      <td>{row.stockingPointCode}</td>
                      <td>{row.onhandQty}</td>
                      <td>{row.reservedQty}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
            {selectedMasterRolls.length > 0 ? (
              <table className="slitting-data-table slitting-overview-table" style={{ marginTop: '0.75rem' }}>
                <thead>
                  <tr>
                    <th>母卷</th>
                    <th>宽×长 (mm)</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedMasterRolls.map((roll) => (
                    <tr key={roll.rollCode}>
                      <td>{roll.rollCode}</td>
                      <td>
                        {roll.widthMm}×{roll.lengthMm}
                      </td>
                      <td>{roll.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
            {selectedInventory.length === 0 && selectedMasterRolls.length === 0 ? (
              <p className="slitting-props-empty">该物料暂无库存记录</p>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}
