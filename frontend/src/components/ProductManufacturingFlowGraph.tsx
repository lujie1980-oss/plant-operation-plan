import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';

import type { MfgFlowBomStage, MfgFlowMaterial, MfgFlowOperation } from '../utils/productManufacturingFlow';

import './ProductManufacturingFlowGraph.css';



const MIN_ZOOM = 0.4;

const MAX_ZOOM = 2.5;

const ZOOM_STEP = 0.1;



function clampZoom(value: number): number {

  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, +value.toFixed(2)));

}



interface ProductManufacturingFlowGraphProps {

  root: MfgFlowBomStage | null;

  productCode: string | null;

}



function MaterialTriangle({ material }: { material: MfgFlowMaterial }) {

  return (

    <div className={`mfg-material-node kind-${material.kind}`} title={material.tooltip}>

      <div className="mfg-triangle-shape" aria-hidden />

      <span className="mfg-material-code">{material.productCode}</span>

    </div>

  );

}



function OperationRect({ operation }: { operation: MfgFlowOperation }) {

  return (

    <div className="mfg-operation-node" title={operation.tooltip}>

      <span className="mfg-operation-name">{operation.operationName}</span>

    </div>

  );

}



function OperationsGroup({ operations }: { operations: MfgFlowOperation[] }) {

  if (operations.length === 0) {

    return null;

  }



  const chain = (

    <div className="mfg-ops-row">

      {operations.map((op, index) => (

        <div key={op.id} className="mfg-op-chain-item">

          {index > 0 && <span className="mfg-connector mfg-connector-right" aria-hidden />}

          <OperationRect operation={op} />

        </div>

      ))}

    </div>

  );



  if (operations.length === 1) {

    return chain;

  }



  return (

    <div className="mfg-ops-group-box" title="工艺路径">

      {chain}

    </div>

  );

}



function FlowStage({ stage }: { stage: MfgFlowBomStage }) {

  const inputs = stage.children.length > 0 ? stage.children : null;

  const leaves = stage.leafInputs;

  const inputCount = inputs ? inputs.length : leaves.length;

  const hasOps = stage.operations.length > 0;

  const hasInputs = inputCount > 0;



  return (

    <div className="mfg-stage-lr" data-level={stage.bomLevel}>

      {hasInputs && (

        <>

          <div className={`mfg-stage-inputs count-${inputCount}`}>

            {inputs

              ? inputs.map((child) => (

                  <div key={child.stageId} className="mfg-input-branch-lr">

                    <FlowStage stage={child} />

                  </div>

                ))

              : leaves.map((leaf) => (

                  <div key={leaf.id} className="mfg-input-branch-lr">

                    <MaterialTriangle material={leaf} />

                  </div>

                ))}

            {inputCount > 1 && <div className="mfg-merge-bar" aria-hidden />}

          </div>

          <span className="mfg-connector mfg-connector-right" aria-hidden />

        </>

      )}



      {hasOps && (

        <>

          <OperationsGroup operations={stage.operations} />

          <span className="mfg-connector mfg-connector-right" aria-hidden />

        </>

      )}



      <MaterialTriangle material={stage.material} />

    </div>

  );

}



export function ProductManufacturingFlowGraph({ root, productCode }: ProductManufacturingFlowGraphProps) {

  const [zoom, setZoom] = useState(1);

  const scrollRef = useRef<HTMLDivElement>(null);



  useEffect(() => {

    setZoom(1);

  }, [productCode]);



  const zoomIn = () => setZoom((z) => clampZoom(z + ZOOM_STEP));

  const zoomOut = () => setZoom((z) => clampZoom(z - ZOOM_STEP));

  const resetZoom = () => setZoom(1);



  const onWheel = useCallback((e: WheelEvent) => {

    if (!e.ctrlKey && !e.metaKey) {

      return;

    }

    e.preventDefault();

    const delta = e.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP;

    setZoom((z) => clampZoom(z + delta));

  }, []);



  useEffect(() => {

    const el = scrollRef.current;

    if (!el) {

      return;

    }

    el.addEventListener('wheel', onWheel, { passive: false });

    return () => el.removeEventListener('wheel', onWheel);

  }, [onWheel]);



  if (!productCode) {

    return <p className="md-summary-empty">请选择成品物料查看制造流程图</p>;

  }



  if (!root) {

    return <p className="md-summary-empty">暂无流程数据（请维护 BOM 与工艺路径）</p>;

  }



  return (

    <div className="mfg-flow-graph">

      <div className="mfg-flow-legend">

        <span>

          <i className="legend-triangle raw" /> 物料（三角，尖头朝上）

        </span>

        <span>

          <i className="legend-rect" /> 工序（圆角矩形）

        </span>

        <span className="legend-hint">原料在左 · 成品在右 · Ctrl+滚轮缩放</span>

        <div className="mfg-flow-zoom-controls">

          <button type="button" className="mfg-zoom-btn" onClick={zoomOut} disabled={zoom <= MIN_ZOOM} aria-label="缩小">

            −

          </button>

          <span className="mfg-zoom-label">{Math.round(zoom * 100)}%</span>

          <button type="button" className="mfg-zoom-btn" onClick={zoomIn} disabled={zoom >= MAX_ZOOM} aria-label="放大">

            +

          </button>

          <button type="button" className="mfg-zoom-btn mfg-zoom-reset" onClick={resetZoom} disabled={zoom === 1}>

            重置

          </button>

        </div>

      </div>

      <div className="mfg-flow-scroll" ref={scrollRef}>

        <div className="mfg-flow-zoom-layer" style={{ '--mfg-zoom': zoom } as CSSProperties}>

          <div className="mfg-flow-canvas">

            <FlowStage stage={root} />

          </div>

        </div>

      </div>

    </div>

  );

}

