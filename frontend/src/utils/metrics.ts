import type { SprintData } from '../context/SprintContext';

export function buildBurndownData(sprint: SprintData | null, today = new Date()) {
  if (!sprint || !sprint.burndownPoints.length) return [];
  const start = Date.parse(sprint.startDate);
  const end = Date.parse(sprint.endDate);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return [];
  const days = Math.round((end - start) / 86_400_000);
  if (days > 366) return [];
  const total = sprint.subtasks.reduce((sum, task) => sum + task.effortPoints, 0);
  const todayDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const remainingByDay = new Map(sprint.burndownPoints.map(point => [point.day, point.remainingPoints]));
  return Array.from({ length: days + 1 }, (_, index) => {
    const day = new Date(start + index * 86_400_000).toISOString().slice(0, 10);
    return {
      day,
      Ideal: days === 0 ? 0 : Math.max(0, Number((total * (1 - index / days)).toFixed(1))),
      Actual: day <= todayDate ? remainingByDay.get(day) ?? null : null,
    };
  });
}
