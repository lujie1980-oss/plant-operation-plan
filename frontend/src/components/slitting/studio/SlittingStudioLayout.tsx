import type { ReactNode } from 'react';
import { VerticalResizeSplit } from '../../VerticalResizeSplit';

type Props = {
  masterPool: ReactNode;
  orderPool: ReactNode;
  tree: ReactNode;
  canvas: ReactNode;
};

/** 2×2 工作室：上排池表可拖拽调高，下排树 + 画板 */
export function SlittingStudioLayout({ masterPool, orderPool, tree, canvas }: Props) {
  return (
    <VerticalResizeSplit
      className="slitting-studio-outer-split"
      storageKey="slitting-studio-row-split"
      minTopRatio={0.22}
      maxTopRatio={0.52}
      defaultTopRatio={0.32}
      collapsible
      collapseBarLabel="母卷 / 订单池"
      top={
        <div className="slitting-studio-pools-row">
          {masterPool}
          {orderPool}
        </div>
      }
      bottom={
        <div className="slitting-studio-work-row">
          {tree}
          {canvas}
        </div>
      }
    />
  );
}
