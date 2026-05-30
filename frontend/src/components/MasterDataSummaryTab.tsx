import { useEffect, useMemo, useState } from 'react';

import { api } from '../api/client';

import { BomHierarchyTree } from './BomHierarchyTree';

import { HorizontalResizeSplit } from './HorizontalResizeSplit';

import { ProductManufacturingFlowGraph } from './ProductManufacturingFlowGraph';

import { ProductRoutingStepsPanel } from './ProductRoutingStepsPanel';

import { VerticalResizeSplit } from './VerticalResizeSplit';

import type { BomMd, MaterialMd, ProductResourceMd, ProductionLineMd, ResourceMd } from '../types/masterData';

import {

  buildBomTree,

  listFinishedProducts,

  type BomTreeNode,

} from '../utils/bomTree';

import { FilterableTable } from './table/FilterableTable';
import { buildManufacturingFlowTree } from '../utils/productManufacturingFlow';

import './MasterDataSummaryTab.css';



interface MasterDataSummaryTabProps {

  dataRevision?: number;

}



export function MasterDataSummaryTab({ dataRevision = 0 }: MasterDataSummaryTabProps) {

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const [materials, setMaterials] = useState<MaterialMd[]>([]);

  const [boms, setBoms] = useState<BomMd[]>([]);

  const [productResources, setProductResources] = useState<ProductResourceMd[]>([]);

  const [resources, setResources] = useState<ResourceMd[]>([]);

  const [lines, setLines] = useState<ProductionLineMd[]>([]);

  const [search, setSearch] = useState('');

  const [selectedProductCode, setSelectedProductCode] = useState<string | null>(null);

  const [selectedBomNodeCode, setSelectedBomNodeCode] = useState<string | null>(null);



  useEffect(() => {

    let cancelled = false;

    setLoading(true);

    setError(null);

    void Promise.all([

      api.masterData.materials.list(),

      api.masterData.boms.list(),

      api.masterData.productResources.list(),

      api.masterData.resources.list(),

      api.masterData.lines.list(),

    ])

      .then(([mat, bomRows, routing, res, lineRows]) => {

        if (cancelled) return;

        setMaterials(mat);

        setBoms(bomRows);

        setProductResources(routing);

        setResources(res);

        setLines(lineRows);

      })

      .catch((err: unknown) => {

        if (!cancelled) {

          setError(err instanceof Error ? err.message : '加载主数据失败');

        }

      })

      .finally(() => {

        if (!cancelled) setLoading(false);

      });

    return () => {

      cancelled = true;

    };

  }, [dataRevision]);



  const finishedProducts = useMemo(

    () => listFinishedProducts(boms, materials, productResources),

    [boms, materials, productResources],

  );



  const filteredProducts = useMemo(() => {

    const q = search.trim().toLowerCase();

    if (!q) return finishedProducts;

    return finishedProducts.filter(

      (p) =>

        p.materialCode.toLowerCase().includes(q) ||

        (p.materialName?.toLowerCase().includes(q) ?? false) ||

        (p.materialType?.toLowerCase().includes(q) ?? false) ||

        (p.siteCode?.toLowerCase().includes(q) ?? false),

    );

  }, [finishedProducts, search]);



  useEffect(() => {

    if (filteredProducts.length === 0) {

      setSelectedProductCode(null);

      setSelectedBomNodeCode(null);

      return;

    }

    if (

      !selectedProductCode ||

      !filteredProducts.some((p) => p.materialCode === selectedProductCode)

    ) {

      const first = filteredProducts[0].materialCode;

      setSelectedProductCode(first);

      setSelectedBomNodeCode(first);

    }

  }, [filteredProducts, selectedProductCode]);



  const materialByCode = useMemo(

    () => new Map(materials.map((m) => [m.materialCode, m])),

    [materials],

  );



  const bomRoot = useMemo((): BomTreeNode | null => {

    if (!selectedProductCode) return null;

    const mat = materialByCode.get(selectedProductCode);

    const children = buildBomTree(boms, materials, selectedProductCode, selectedProductCode);

    return {

      productCode: selectedProductCode,

      productName: mat?.materialName ?? null,

      materialType: mat?.materialType ?? null,

      uomCode: mat?.uomCode ?? null,

      siteCode: mat?.siteCode ?? null,

      qty: 1,

      isCritical: true,

      scrapRate: null,

      bomId: null,

      bomVersion: null,

      bomEffectiveFrom: null,

      bomEffectiveTo: null,

      componentEffectiveFrom: null,

      componentEffectiveTo: null,

      children,

    };

  }, [boms, materials, selectedProductCode, materialByCode]);



  const routingForSelection = useMemo(() => {

    if (!selectedBomNodeCode) return [];

    return productResources.filter((r) => r.productCode === selectedBomNodeCode);

  }, [productResources, selectedBomNodeCode]);



  const flowRoot = useMemo(() => {

    if (!selectedProductCode || !bomRoot) {

      return null;

    }

    const mat = materialByCode.get(selectedProductCode);

    return buildManufacturingFlowTree(

      selectedProductCode,

      mat?.materialName ?? null,

      bomRoot.children,

      productResources,

      resources,

      lines,

    );

  }, [selectedProductCode, bomRoot, productResources, resources, lines, materialByCode]);



  const onSelectProduct = (code: string) => {

    setSelectedProductCode(code);

    setSelectedBomNodeCode(code);

  };



  if (loading) {

    return <p className="md-summary-empty">加载中…</p>;

  }



  if (error) {

    return <p className="md-summary-error">{error}</p>;

  }



  const selectedBomName = selectedBomNodeCode

    ? (materialByCode.get(selectedBomNodeCode)?.materialName ?? null)

    : null;



  return (

    <div className="md-summary-tab">

      <p className="md-summary-desc">

        选择成品物料，查看多级 BOM、各层级工艺路径，以及从原料到成品的制造流程图（左→右）。

      </p>



      <VerticalResizeSplit

        storageKey="md-summary-split-ratio"

        minTopRatio={0.38}

        maxTopRatio={0.65}

        className="md-summary-split"

        top={

          <HorizontalResizeSplit

            storageKey="md-summary-top-split"

            minLeftRatio={0.22}

            maxLeftRatio={0.55}

            className="md-summary-row md-summary-row-top"

            left={

              <section className="md-summary-panel md-summary-products">

                <header className="md-summary-panel-head">

                  <h3>成品物料</h3>

                  <input

                    type="search"

                    className="md-summary-search"

                    placeholder="搜索料号 / 名称"

                    value={search}

                    onChange={(e) => setSearch(e.target.value)}

                  />

                </header>

                <div className="md-summary-product-table-wrap">
                  <FilterableTable
                    tableId="md-summary-products"
                    tableClassName="md-summary-product-table"
                    wrapClassName="md-summary-product-table-wrap-inner"
                    rows={filteredProducts}
                    rowKey={(p) => p.materialCode}
                    emptyText="暂无成品物料（请维护 BOM 成品料号）"
                    onRowClick={(p) => onSelectProduct(p.materialCode)}
                    getRowClassName={(p) =>
                      selectedProductCode === p.materialCode ? 'is-selected' : ''
                    }
                    cellWrap
                    columns={[
                      {
                        key: 'materialCode',
                        header: '物料编码',
                        width: 120,
                        render: (p) => <span className="md-summary-td-code">{p.materialCode}</span>,
                        getFilterText: (p) => p.materialCode,
                      },
                      {
                        key: 'materialName',
                        header: '物料名称',
                        width: 120,
                        render: (p) => p.materialName ?? '—',
                        getFilterText: (p) => p.materialName ?? '',
                      },
                      {
                        key: 'materialType',
                        header: '物料类型',
                        width: 90,
                        render: (p) => p.materialType ?? '—',
                        getFilterText: (p) => p.materialType ?? '',
                      },
                      {
                        key: 'siteCode',
                        header: '基地',
                        width: 72,
                        render: (p) => p.siteCode ?? '—',
                        getFilterText: (p) => p.siteCode ?? '',
                      },
                      {
                        key: 'uomCode',
                        header: '单位',
                        width: 56,
                        render: (p) => p.uomCode ?? '—',
                        getFilterText: (p) => p.uomCode ?? '',
                      },
                    ]}
                  />
                </div>

              </section>

            }

            right={

              <section className="md-summary-panel md-summary-flow">

                <header className="md-summary-panel-head">

                  <h3>制造流程图</h3>

                  {selectedProductCode && (

                    <span className="md-summary-panel-meta">

                      {selectedProductCode}

                      {materialByCode.get(selectedProductCode)?.materialName &&

                        ` · ${materialByCode.get(selectedProductCode)?.materialName}`}

                    </span>

                  )}

                </header>

                <ProductManufacturingFlowGraph root={flowRoot} productCode={selectedProductCode} />

              </section>

            }

          />

        }

        bottom={

          <HorizontalResizeSplit

            storageKey="md-summary-bottom-split"

            minLeftRatio={0.35}

            maxLeftRatio={0.68}

            className="md-summary-row md-summary-row-bottom"

            left={

              <section className="md-summary-panel md-summary-bom">

                <header className="md-summary-panel-head">

                  <h3>BOM 结构</h3>

                  {selectedProductCode && (

                    <span className="md-summary-panel-meta">{selectedProductCode}</span>

                  )}

                </header>

                <BomHierarchyTree

                  key={selectedProductCode ?? 'none'}

                  root={bomRoot}

                  selectedProductCode={selectedBomNodeCode}

                  onSelect={setSelectedBomNodeCode}

                />

              </section>

            }

            right={

              <section className="md-summary-panel md-summary-routing">

                <header className="md-summary-panel-head">

                  <h3>工艺路径</h3>

                </header>

                <ProductRoutingStepsPanel

                  productCode={selectedBomNodeCode}

                  productName={selectedBomName}

                  steps={routingForSelection}

                  resources={resources}

                  lines={lines}

                />

              </section>

            }

          />

        }

      />

    </div>

  );

}

