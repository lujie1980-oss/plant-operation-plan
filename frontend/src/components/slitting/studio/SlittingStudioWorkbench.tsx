import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../../api/client';
import { slittingClient } from '../../../api/slittingClient';
import type { BomMd, MaterialMd } from '../../../types/masterData';
import { StatusBanner } from '../../StatusBanner';
import { CreateRegionDialog } from './CreateRegionDialog';
import { DemandPoolPanel } from './DemandPoolPanel';
import { OrderOnMasterDialog } from './OrderOnMasterDialog';
import { OrientationDialog } from './OrientationDialog';
import { StudioCanvas } from './StudioCanvas';
import { RegionSizePanel } from './RegionSizePanel';
import { SlittingStudioLayout } from './SlittingStudioLayout';
import { SlittingStudioToolbar } from './SlittingStudioToolbar';
import { StudioTreePanel } from './StudioTreePanel';
import { buildBomSourceRows, SourcePoolPanel } from './SourcePoolPanel';
import { useSlittingStudioStore } from '../../../store/slitting/studioStore';
import { MaterialCatalog } from '../../../utils/materialCatalog';
import { filterBomsToMaterialMaster } from '../../../utils/bomTree';
import { slittingNodeLabel, slittingNodeSubtitle } from '../../../utils/slitting/display';
import { nodeLength, nodeWidth } from '../../../utils/slitting/studioGeometry';
import { studioToTreePayload, treeToStudio } from '../../../utils/slitting/studioPersist';
import type { BomLevel, SourceMode, StudioSourceSelection } from '../../../utils/slitting/studioBomLevels';
import {
  allBomMaterialCodes,
  bomLevelOptions,
  bomMaterialCodesAtLevel,
  inventoryRollsAtLevel,
  maxBomDepth,
  resolvePrimaryFinished,
  resolveSourceForMasterNode,
  slittableDemandsForSource,
  orderFitsMasterRoll,
} from '../../../utils/slitting/studioBomLevels';
import type { MasterRoll } from '../../../types/slitting';
import {
  bomMaterialRollCode,
  buildVirtualMasterFromBom,
  isCatalogMasterRoll,
  resolveInventoryMasterForBom,
} from '../../../utils/slitting/virtualMasterFromBom';

/** 母卷分切工作台（左上来源 / 右上需求 / 分切树 / 图形化） */
export function SlittingStudioWorkbench() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [err, setErr] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [optimizing, setOptimizing] = useState(false);
  const [plans, setPlans] = useState<{ planVersionId: string; name: string }[]>([]);
  const [regionDialog, setRegionDialog] = useState<{ nodeId: string } | null>(null);
  const [orderDialog, setOrderDialog] = useState<{ orderCode: string; regionNodeId: string } | null>(null);
  const [masterOrderDialog, setMasterOrderDialog] = useState<{ orderCode: string; masterNodeId: string } | null>(
    null,
  );
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [boms, setBoms] = useState<BomMd[]>([]);
  const [materials, setMaterials] = useState<MaterialMd[]>([]);
  const [sourceMode, setSourceMode] = useState<SourceMode>('inventory');
  const [bomLevel, setBomLevel] = useState<BomLevel>(1);
  const [selectedSource, setSelectedSource] = useState<StudioSourceSelection | null>(null);

  const {
    planVersionId,
    planName,
    masters,
    orders,
    nodes,
    assignments,
    canvasMasterId,
    selectedNodeId,
    allMasters,
    allOrders,
    setCatalog,
    setPlan,
    addMasterNode,
    createRegions,
    ensureFullRegionOnMaster,
    placeOrder,
    resizeRegion,
    deleteNode,
    selectNode,
    toggleNodeLock,
  } = useSlittingStudioStore();

  const nodeById = useMemo(() => new Map(nodes.map((n) => [n.nodeId, n])), [nodes]);
  const canvasMaster = canvasMasterId ? nodeById.get(canvasMasterId) ?? null : null;
  const regionDialogTarget = regionDialog ? nodeById.get(regionDialog.nodeId) : null;
  const selectedNode = selectedNodeId ? nodeById.get(selectedNodeId) ?? null : null;
  const selectedRegionCtx = useMemo(() => {
    if (!selectedNode || selectedNode.nodeType !== 'INTERMEDIATE') return null;
    const assignment = assignments.find((a) => a.childNodeId === selectedNode.nodeId);
    if (!assignment) return null;
    const parent = nodeById.get(assignment.parentNodeId);
    if (!parent) return null;
    return { region: selectedNode, parent, assignment };
  }, [selectedNode, assignments, nodeById]);

  const catalog = useMemo(() => new MaterialCatalog(materials), [materials]);
  const scopedBoms = useMemo(() => filterBomsToMaterialMaster(boms, catalog), [boms, catalog]);
  const finishedRoot = useMemo(() => resolvePrimaryFinished(scopedBoms), [scopedBoms]);

  const maxBomLevel = useMemo(() => maxBomDepth(scopedBoms, finishedRoot), [scopedBoms, finishedRoot]);
  const availableBomLevels = useMemo(() => bomLevelOptions(maxBomLevel), [maxBomLevel]);

  useEffect(() => {
    if (bomLevel > maxBomLevel) {
      setBomLevel(maxBomLevel);
    }
  }, [bomLevel, maxBomLevel]);

  const allBomProductCodes = useMemo(
    () => allBomMaterialCodes(scopedBoms, finishedRoot, catalog),
    [scopedBoms, finishedRoot, catalog],
  );

  const inventoryRows = useMemo(
    () => inventoryRollsAtLevel(allMasters, scopedBoms, finishedRoot, bomLevel, catalog),
    [allMasters, scopedBoms, finishedRoot, bomLevel, catalog],
  );

  const bomRows = useMemo(
    () =>
      buildBomSourceRows(
        bomMaterialCodesAtLevel(scopedBoms, finishedRoot, bomLevel, catalog),
        bomLevel,
        catalog,
      ),
    [scopedBoms, finishedRoot, bomLevel, catalog],
  );

  const slittableDemands = useMemo(() => {
    if (!selectedSource) return [];
    return slittableDemandsForSource(selectedSource, scopedBoms, finishedRoot, allOrders);
  }, [selectedSource, scopedBoms, finishedRoot, allOrders]);

  const sourceLabel = useMemo(() => {
    if (!selectedSource) return '—';
    if (selectedSource.kind === 'roll') {
      return `母卷 ${selectedSource.rollCode}`;
    }
    return `${catalog.materialName(selectedSource.productCode)} (${selectedSource.productCode})`;
  }, [selectedSource, catalog]);

  const handleSelectSource = useCallback(
    (source: StudioSourceSelection | null) => {
      setSelectedSource(source);
      if (source?.kind === 'roll') {
        const nodeId = `MASTER-${source.rollCode}`;
        if (nodes.some((n) => n.nodeId === nodeId)) {
          selectNode(nodeId);
        }
      } else if (source?.kind === 'bom') {
        const nodeId = `MASTER-${bomMaterialRollCode(source.productCode)}`;
        if (nodes.some((n) => n.nodeId === nodeId)) {
          selectNode(nodeId);
        }
      }
    },
    [nodes, selectNode],
  );

  const persist = useCallback(async () => {
    const id = useSlittingStudioStore.getState().planVersionId;
    if (!id) return;
    const snap = useSlittingStudioStore.getState().getSnapshot();
    const payload = studioToTreePayload(snap.nodes, snap.assignments);
    setSaving(true);
    try {
      await slittingClient.saveTree(id, payload.nodes, payload.assignments);
      setSuccess('已自动保存');
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, []);

  const scheduleSave = useCallback(() => {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => void persist(), 800);
  }, [persist]);

  useEffect(() => {
    void Promise.all([
      slittingClient.listMasterRolls(),
      slittingClient.listChildOrders(),
      slittingClient.listPlans(),
      api.masterData.boms.list(),
      api.masterData.materials.list(),
    ])
      .then(([m, o, p, b, mats]) => {
        setCatalog(m, o);
        setBoms(b);
        setMaterials(mats);
        setPlans(p.map((x) => ({ planVersionId: x.planVersionId, name: x.name })));
      })
      .catch((e: unknown) => setErr(e instanceof Error ? e.message : String(e)));
  }, [setCatalog]);

  const loadPlan = useCallback(
    async (id: string) => {
      setErr(null);
      const tree = await slittingClient.getTree(id);
      const studio = treeToStudio(tree);
      const summary = await slittingClient.listPlans().then((list) => list.find((p) => p.planVersionId === id));
      setPlan(id, summary?.name ?? id, studio.nodes, studio.assignments);
      setSearchParams({ plan: id });
    },
    [setPlan, setSearchParams],
  );

  useEffect(() => {
    const plan = searchParams.get('plan');
    if (plan && plan !== planVersionId) {
      void loadPlan(plan).catch((e: unknown) => setErr(e instanceof Error ? e.message : String(e)));
    }
  }, [searchParams, planVersionId, loadPlan]);

  useEffect(() => {
    if (!planVersionId) return;
    scheduleSave();
  }, [nodes, assignments, planVersionId, scheduleSave]);

  const ensurePlanAndAddRoll = async (roll: MasterRoll, sourceSelection?: StudioSourceSelection) => {
    setErr(null);
    const nodeId = `MASTER-${roll.rollCode}`;
    if (nodes.some((n) => n.nodeId === nodeId)) {
      selectNode(nodeId);
      if (sourceSelection) {
        setSelectedSource(sourceSelection);
      }
      setSuccess(`${roll.rollCode} 已在分切树中`);
      return;
    }
    try {
      let id = planVersionId;
      const linkToCatalog = isCatalogMasterRoll(roll, allMasters);
      if (!id) {
        const created = await slittingClient.createPlan({
          name: `工作台-${new Date().toLocaleDateString('zh-CN')}`,
          masterRollCodes: linkToCatalog ? [roll.rollCode] : [],
          childOrderCodes: [],
        });
        id = created.planVersionId;
        setPlans((prev) => [...prev, { planVersionId: id!, name: created.name }]);
        setPlan(id, created.name, [], []);
        setSearchParams({ plan: id });
      }
      addMasterNode(roll);
      scheduleSave();
      if (sourceSelection) {
        setSelectedSource(sourceSelection);
      }
      setSuccess(linkToCatalog ? `已加入母卷 ${roll.rollCode}` : `已加入 BOM 物料 ${roll.productCode ?? roll.rollCode}`);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  };

  const ensurePlanAndAddMaster = async (rollCode: string) => {
    const roll = allMasters.find((m) => m.rollCode === rollCode);
    if (!roll) return;
    await ensurePlanAndAddRoll(roll, {
      kind: 'roll',
      rollCode: roll.rollCode,
      productCode: roll.productCode ?? roll.finishedProductCode ?? roll.materialCode ?? '',
    });
  };

  const ensurePlanAndAddBomMaterial = async (productCode: string) => {
    const usedRollCodes = new Set(
      nodes.filter((n) => n.nodeType === 'MASTER').map((n) => n.nodeId.replace(/^MASTER-/, '')),
    );
    const inventoryRoll = resolveInventoryMasterForBom(productCode, allMasters, usedRollCodes);
    const roll =
      inventoryRoll ?? buildVirtualMasterFromBom(productCode, finishedRoot || undefined);
    const sourceSelection: StudioSourceSelection = inventoryRoll
      ? {
          kind: 'roll',
          rollCode: inventoryRoll.rollCode,
          productCode,
        }
      : { kind: 'bom', productCode };
    await ensurePlanAndAddRoll(roll, sourceSelection);
  };

  const openOrderPlace = (orderCode: string, regionNodeId: string) => {
    setOrderDialog({ orderCode, regionNodeId });
  };

  const openOrderOnMaster = (orderCode: string, masterNodeId: string) => {
    setMasterOrderDialog({ orderCode, masterNodeId });
    selectNode(masterNodeId);
  };

  const handleOrderPlace = (orientation: 'horizontal' | 'vertical') => {
    if (!orderDialog) return;
    const result = placeOrder({
      regionNodeId: orderDialog.regionNodeId,
      orderCode: orderDialog.orderCode,
      orientation,
    });
    setOrderDialog(null);
    if (!result.ok) {
      setErr(result.message);
      return;
    }
    setSuccess('订单已放入区域');
    scheduleSave();
  };

  const proceedAutoFullRegionAndPlace = () => {
    if (!masterOrderDialog) return;
    const prep = ensureFullRegionOnMaster(masterOrderDialog.masterNodeId);
    setMasterOrderDialog(null);
    if (!prep.ok) {
      setErr(prep.message);
      return;
    }
    selectNode(prep.regionNodeId);
    openOrderPlace(masterOrderDialog.orderCode, prep.regionNodeId);
  };

  const newDraftPlan = async () => {
    setErr(null);
    try {
      useSlittingStudioStore.getState().clearPlan();
      const created = await slittingClient.createPlan({
        name: `工作台-${new Date().toLocaleDateString('zh-CN')}`,
        masterRollCodes: [],
        childOrderCodes: [],
      });
      setPlans((prev) => [...prev, { planVersionId: created.planVersionId, name: created.name }]);
      setPlan(created.planVersionId, created.name, [], []);
      setSearchParams({ plan: created.planVersionId });
      setSuccess('已新建空白方案，请从左上选择母卷并拖入分切树');
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  };

  const bomProductCodes = allBomProductCodes;

  const orderCodesForMaster = useCallback(
    (masterNodeId: string) => {
      const source = resolveSourceForMasterNode(masterNodeId, allMasters, bomProductCodes);
      if (!source) return [];
      const rollCode = masterNodeId.replace(/^MASTER-/, '');
      const masterRoll = allMasters.find((m) => m.rollCode === rollCode);
      const masterNode = nodes.find((n) => n.nodeId === masterNodeId);
      const masterWidth = masterRoll?.widthMm ?? masterNode?.widthMm ?? 0;
      const masterLength = masterRoll?.lengthMm ?? masterNode?.lengthMm ?? 0;
      return slittableDemandsForSource(source, scopedBoms, finishedRoot, allOrders)
        .filter((o) => orderFitsMasterRoll(o, masterWidth, masterLength))
        .map((o) => o.orderCode);
    },
    [allMasters, bomProductCodes, scopedBoms, finishedRoot, allOrders, nodes],
  );

  const applyOptimizedTree = useCallback(
    (tree: Awaited<ReturnType<typeof slittingClient.getTree>>) => {
      const studio = treeToStudio(tree);
      const id = useSlittingStudioStore.getState().planVersionId;
      const name = useSlittingStudioStore.getState().planName;
      if (id) {
        setPlan(id, name, studio.nodes, studio.assignments);
      }
    },
    [setPlan],
  );

  const handleAutoSlitMaster = useCallback(
    async (masterNodeId: string) => {
      if (!planVersionId) {
        setErr('请先保存或选择方案');
        return;
      }
      setErr(null);
      setOptimizing(true);
      try {
        await persist();
        const orderCodes = orderCodesForMaster(masterNodeId);
        const tree = await slittingClient.optimizeMaster(planVersionId, masterNodeId, orderCodes);
        applyOptimizedTree(tree);
        selectNode(masterNodeId);
        setSuccess('母卷自动分切完成（已锁定项保持不变）');
      } catch (e: unknown) {
        setErr(e instanceof Error ? e.message : String(e));
      } finally {
        setOptimizing(false);
      }
    },
    [planVersionId, persist, orderCodesForMaster, applyOptimizedTree, selectNode],
  );

  const handleOptimizeAllUnlocked = useCallback(async () => {
    if (!planVersionId) {
      setErr('请先保存或选择方案');
      return;
    }
    const masters = nodes.filter((n) => n.nodeType === 'MASTER');
    if (masters.length === 0) {
      setErr('分切树中尚无母卷');
      return;
    }
    setErr(null);
    setOptimizing(true);
    try {
      await persist();
      let tree: Awaited<ReturnType<typeof slittingClient.getTree>> | null = null;
      for (const master of masters) {
        const orderCodes = orderCodesForMaster(master.nodeId);
        tree = await slittingClient.optimizeMaster(planVersionId, master.nodeId, orderCodes);
      }
      if (tree) {
        applyOptimizedTree(tree);
      }
      setSuccess('已重新优化全部未锁定分切（已锁定项保持不变）');
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setOptimizing(false);
    }
  }, [planVersionId, nodes, persist, orderCodesForMaster, applyOptimizedTree]);

  const handleToggleLock = useCallback(
    (nodeId: string) => {
      toggleNodeLock(nodeId);
      scheduleSave();
      setSuccess('锁定状态已更新');
    },
    [toggleNodeLock, scheduleSave],
  );

  const handleCreateFullRegion = (nodeId: string) => {
    try {
      createRegions({ targetNodeId: nodeId, direction: 'horizontal', cutSizeMm: 0, mode: 'full' });
      setSuccess('已创建整卷区域');
      scheduleSave();
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <>
      <StatusBanner error={err} success={success} />
      <SlittingStudioToolbar
        plans={plans}
        planVersionId={planVersionId}
        planName={planName}
        saving={saving}
        optimizing={optimizing}
        masterCount={masters.length}
        orderCount={orders.length}
        onPlanChange={(id) => void loadPlan(id)}
        onNewPlan={() => void newDraftPlan()}
        onSave={() => void persist()}
        onOptimizeAll={() => void handleOptimizeAllUnlocked()}
      />
      <SlittingStudioLayout
        masterPool={
          <SourcePoolPanel
            sourceMode={sourceMode}
            bomLevel={bomLevel}
            bomLevelOptions={availableBomLevels}
            onSourceModeChange={(mode) => {
              setSourceMode(mode);
              setSelectedSource(null);
            }}
            onBomLevelChange={(level) => {
              setBomLevel(level);
              setSelectedSource(null);
            }}
            inventoryRows={inventoryRows}
            bomRows={bomRows}
            selectedSource={selectedSource}
            onSelectSource={handleSelectSource}
          />
        }
        orderPool={
          <DemandPoolPanel
            demands={slittableDemands}
            selectedSource={selectedSource}
            sourceLabel={sourceLabel}
          />
        }
        tree={
          <div className="slitting-studio-tree-wrap">
            <StudioTreePanel
              nodes={nodes}
              assignments={assignments}
              selectedNodeId={selectedNodeId}
              optimizing={optimizing}
              allMasters={allMasters}
              onSelect={selectNode}
              onMasterDrop={(code) => void ensurePlanAndAddMaster(code)}
              onBomMaterialDrop={(code) => void ensurePlanAndAddBomMaterial(code)}
              onOrderDropOnRegion={openOrderPlace}
              onOrderDropOnMaster={openOrderOnMaster}
              onCreateRegion={(nodeId) => setRegionDialog({ nodeId })}
              onCreateFullRegion={handleCreateFullRegion}
              onResizeRegion={(nodeId) => selectNode(nodeId)}
              onDelete={(nodeId) => {
                deleteNode(nodeId);
                scheduleSave();
              }}
              onToggleLock={handleToggleLock}
              onAutoSlitMaster={(masterNodeId) => void handleAutoSlitMaster(masterNodeId)}
            />
            {selectedRegionCtx ? (
              <RegionSizePanel
                region={selectedRegionCtx.region}
                parent={selectedRegionCtx.parent}
                assignment={selectedRegionCtx.assignment}
                onClose={() => selectNode(null)}
                onApply={(lengthMm, widthMm) => {
                  const result = resizeRegion(selectedRegionCtx.region.nodeId, lengthMm, widthMm);
                  if (!result.ok) return result;
                  setSuccess('区域尺寸已更新');
                  scheduleSave();
                  return result;
                }}
              />
            ) : null}
          </div>
        }
        canvas={
          <StudioCanvas
            masterNode={canvasMaster}
            nodes={nodes}
            assignments={assignments}
            selectedNodeId={selectedNodeId}
            allOrders={allOrders}
            allMasters={allMasters}
            onSelectNode={selectNode}
            onOrderDropOnMaster={openOrderOnMaster}
          />
        }
      />
      <CreateRegionDialog
        open={Boolean(regionDialog && regionDialogTarget)}
        targetLabel={
          regionDialogTarget
            ? `${slittingNodeLabel(regionDialogTarget)} (${slittingNodeSubtitle(regionDialogTarget)})`
            : ''
        }
        maxHorizontalMm={regionDialogTarget ? nodeLength(regionDialogTarget) : 0}
        maxVerticalMm={regionDialogTarget ? nodeWidth(regionDialogTarget) : 0}
        onClose={() => setRegionDialog(null)}
        onConfirm={(mode, direction, cutSizeMm) => {
          if (!regionDialog) return;
          try {
            createRegions({ targetNodeId: regionDialog.nodeId, direction, cutSizeMm, mode });
            setSuccess(mode === 'full' ? '已创建整卷区域' : '已创建两个区域');
            scheduleSave();
          } catch (e: unknown) {
            setErr(e instanceof Error ? e.message : String(e));
          }
        }}
      />
      <OrderOnMasterDialog
        open={Boolean(masterOrderDialog)}
        masterLabel={
          masterOrderDialog ? slittingNodeLabel(nodeById.get(masterOrderDialog.masterNodeId)!) : ''
        }
        orderCode={masterOrderDialog?.orderCode ?? ''}
        onClose={() => setMasterOrderDialog(null)}
        onManualRegion={() => {
          if (!masterOrderDialog) return;
          const { masterNodeId, orderCode } = masterOrderDialog;
          setMasterOrderDialog(null);
          setRegionDialog({ nodeId: masterNodeId });
          setSuccess(`请创建区域后再拖入订单 ${orderCode}`);
        }}
        onAutoFullRegion={proceedAutoFullRegionAndPlace}
      />
      <OrientationDialog
        open={Boolean(orderDialog)}
        order={orderDialog ? allOrders.find((o) => o.orderCode === orderDialog.orderCode) ?? null : null}
        regionLabel={
          orderDialog && nodeById.get(orderDialog.regionNodeId)
            ? slittingNodeLabel(nodeById.get(orderDialog.regionNodeId)!)
            : ''
        }
        onClose={() => setOrderDialog(null)}
        onConfirm={handleOrderPlace}
      />
    </>
  );
}
