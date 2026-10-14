import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import type { DragEndEvent, DragOverEvent, DragStartEvent } from '@dnd-kit/core';
import type { Subtask } from '../src/context/SprintContext';
import KanbanBoard from '../src/components/kanban/KanbanBoard';

const fixture = vi.hoisted(() => ({
  role: 'MEMBER',
  update: vi.fn(),
  handlers: {} as {
    onDragStart: (event: DragStartEvent) => void;
    onDragOver: (event: DragOverEvent) => void;
    onDragEnd: (event: DragEndEvent) => Promise<void>;
    onDragCancel: () => void;
  },
  sprint: { id: 1, sprintStatus: 'ACTIVE', subtasks: [{ id: 1, title: 'Task', status: 'TODO', effortPoints: 3 }] },
}));
vi.mock('../src/context/SprintContext', () => ({ useSprint: () => ({ sprint: fixture.sprint, loading: false, updateSubtaskStatus: fixture.update, refreshData: vi.fn() }) }));
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ role: fixture.role }) }));
vi.mock('../src/api/axios', () => ({ getErrorMessage: (error: Error) => error.message }));
vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children, ...handlers }: { children: ReactNode } & typeof fixture.handlers) => {
    fixture.handlers = handlers;
    return <div>{children}</div>;
  },
  DragOverlay: () => null, closestCorners: vi.fn(), KeyboardSensor: vi.fn(), PointerSensor: vi.fn(), useSensor: vi.fn(), useSensors: vi.fn(),
}));
vi.mock('@dnd-kit/sortable', () => ({ sortableKeyboardCoordinates: vi.fn() }));
vi.mock('../src/components/kanban/TaskCard', () => ({ default: () => null }));
vi.mock('../src/components/kanban/RequirementsPanel', () => ({ default: () => null }));
vi.mock('../src/components/kanban/KanbanColumn', () => ({
  default: ({ id, tasks, disabled }: { id: string; tasks: Subtask[]; disabled: boolean }) => <div data-testid={id} data-disabled={disabled}>{tasks.map(task => task.title).join(',')}</div>,
}));

const active = { id: 1, data: { current: { type: 'Task' } } };
const over = { id: 'DONE', data: { current: { type: 'Column' } } };
function previewMove() {
  act(() => { fixture.handlers.onDragStart({ active } as DragStartEvent); });
  act(() => { fixture.handlers.onDragOver({ active, over } as DragOverEvent); });
}
beforeEach(() => { fixture.update.mockReset(); fixture.role = 'MEMBER'; });
afterEach(cleanup);

describe('Kanban status changes', () => {
  it('restores the original status on cancel and on a drop outside the board', async () => {
    render(<KanbanBoard />);
    previewMove(); expect(screen.getByTestId('DONE').textContent).toBe('Task');
    act(() => { fixture.handlers.onDragCancel(); });
    expect(screen.getByTestId('TODO').textContent).toBe('Task');
    previewMove();
    await act(async () => { await fixture.handlers.onDragEnd({ active, over: null } as DragEndEvent); });
    expect(screen.getByTestId('TODO').textContent).toBe('Task');
    expect(fixture.update).not.toHaveBeenCalled();
  });

  it('rolls back a rejected save and presents its error', async () => {
    fixture.update.mockRejectedValue(new Error('Update forbidden'));
    render(<KanbanBoard />); previewMove();
    await act(async () => { await fixture.handlers.onDragEnd({ active, over } as DragEndEvent); });
    expect(fixture.update).toHaveBeenCalledWith(1, 'DONE');
    expect(screen.getByTestId('TODO').textContent).toBe('Task');
    expect(screen.getByRole('alert').textContent).toBe('Update forbidden');
  });

  it('disables viewer drag interactions', async () => {
    fixture.role = 'VIEWER'; render(<KanbanBoard />); previewMove();
    await act(async () => { await fixture.handlers.onDragEnd({ active, over } as DragEndEvent); });
    expect(screen.getByTestId('TODO').getAttribute('data-disabled')).toBe('true');
    expect(screen.getByTestId('TODO').textContent).toBe('Task');
    expect(fixture.update).not.toHaveBeenCalled();
  });
});
