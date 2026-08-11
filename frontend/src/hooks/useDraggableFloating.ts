import { useCallback, useEffect, useMemo, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';

interface Point { x: number; y: number }
interface DragState extends Point { pointerId: number; originX: number; originY: number }

const readPosition = (storageKey: string, width: number, height: number): Point => {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? '') as Point;
    if (Number.isFinite(stored.x) && Number.isFinite(stored.y)) return stored;
  } catch {
    // First use or stale storage: fall back to the bottom-right corner.
  }
  return { x: Math.max(12, window.innerWidth - width - 24), y: Math.max(12, window.innerHeight - height - 24) };
};

export const useDraggableFloating = (storageKey: string, width: number, height: number) => {
  const [position, setPosition] = useState<Point>(() => readPosition(storageKey, width, height));
  const [drag, setDrag] = useState<DragState | null>(null);

  const clamp = useCallback((point: Point): Point => ({
    x: Math.min(Math.max(8, point.x), Math.max(8, window.innerWidth - width - 8)),
    y: Math.min(Math.max(8, point.y), Math.max(8, window.innerHeight - height - 8)),
  }), [height, width]);

  useEffect(() => {
    const handleResize = () => setPosition((current) => clamp(current));
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [clamp]);

  const dragHandleProps = useMemo(() => ({
    onPointerDown: (event: ReactPointerEvent<HTMLElement>) => {
      if (event.button !== 0) return;
      event.currentTarget.setPointerCapture(event.pointerId);
      setDrag({
        pointerId: event.pointerId,
        originX: event.clientX,
        originY: event.clientY,
        x: position.x,
        y: position.y,
      });
    },
    onPointerMove: (event: ReactPointerEvent<HTMLElement>) => {
      if (!drag || drag.pointerId !== event.pointerId) return;
      setPosition(clamp({
        x: drag.x + event.clientX - drag.originX,
        y: drag.y + event.clientY - drag.originY,
      }));
    },
    onPointerUp: (event: ReactPointerEvent<HTMLElement>) => {
      if (!drag || drag.pointerId !== event.pointerId) return;
      event.currentTarget.releasePointerCapture(event.pointerId);
      const finalPosition = clamp({
        x: drag.x + event.clientX - drag.originX,
        y: drag.y + event.clientY - drag.originY,
      });
      setPosition(finalPosition);
      localStorage.setItem(storageKey, JSON.stringify(finalPosition));
      setDrag(null);
    },
    onPointerCancel: () => setDrag(null),
  }), [clamp, drag, position, storageKey]);

  return { position, dragging: Boolean(drag), dragHandleProps };
};
