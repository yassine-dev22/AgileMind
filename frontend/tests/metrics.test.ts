import { describe, expect, it } from 'vitest';
import type { SprintData } from '../src/context/SprintContext';
import { buildBurndownData } from '../src/utils/metrics';

describe('burndown data', () => {
  it('uses actual API snapshots and sprint dates without inventing future completion', () => {
    const sprint = {
      startDate: '2026-10-01', endDate: '2026-10-04', subtasks: [{ effortPoints: 8 }],
      burndownPoints: [{ day: '2026-10-01', remainingPoints: 8 }, { day: '2026-10-02', remainingPoints: 3 }],
    } as SprintData;
    expect(buildBurndownData(sprint, new Date(2026, 9, 2))).toEqual([
      { day: '2026-10-01', Ideal: 8, Actual: 8 },
      { day: '2026-10-02', Ideal: 5.3, Actual: 3 },
      { day: '2026-10-03', Ideal: 2.7, Actual: null },
      { day: '2026-10-04', Ideal: 0, Actual: null },
    ]);
  });
});
