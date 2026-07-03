export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function effectiveSize(widthMm: number, lengthMm: number, rotated: boolean): { w: number; h: number } {
  return rotated ? { w: lengthMm, h: widthMm } : { w: widthMm, h: lengthMm };
}

export function aabbOverlap(a: Rect, b: Rect): boolean {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export function assignmentRect(
  posXMm: number,
  posYMm: number,
  widthMm: number,
  lengthMm: number,
  rotated: boolean,
): Rect {
  const { w, h } = effectiveSize(widthMm, lengthMm, rotated);
  return { x: posXMm, y: posYMm, w, h };
}

export function collidesWithAny(
  candidate: Rect,
  others: Rect[],
  parentBounds: Rect,
): boolean {
  if (
    candidate.x < parentBounds.x ||
    candidate.y < parentBounds.y ||
    candidate.x + candidate.w > parentBounds.x + parentBounds.w ||
    candidate.y + candidate.h > parentBounds.y + parentBounds.h
  ) {
    return true;
  }
  return others.some((o) => aabbOverlap(candidate, o));
}
