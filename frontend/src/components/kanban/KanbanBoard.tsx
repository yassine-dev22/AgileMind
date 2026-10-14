import React, { useState, useMemo, useRef } from 'react';
import {
  DndContext, DragOverlay, closestCorners, KeyboardSensor, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core';
import type { DragStartEvent, DragOverEvent, DragEndEvent } from '@dnd-kit/core';
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import type { TaskStatus } from '../../context/SprintContext';
import { useAuth } from '../../context/AuthContext';
import { useSprint } from '../../context/SprintContext';
import { getErrorMessage } from '../../api/axios';
import { LayoutTemplate, ListChecks, RotateCcw } from 'lucide-react';
import KanbanColumn from './KanbanColumn';
import TaskCard from './TaskCard';
import RequirementsPanel from './RequirementsPanel';

const COLUMNS: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];
interface Preview { id: number; status: TaskStatus }

const KanbanBoard: React.FC = () => {
  const { sprint, loading, updateSubtaskStatus, refreshData } = useSprint();
  const { role } = useAuth();
  const [preview, setPreview] = useState<Preview | null>(null);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [isReqPanelOpen, setIsReqPanelOpen] = useState(false);
  const canEdit = role !== 'VIEWER' && sprint?.sprintStatus === 'ACTIVE' && !saving;
  const tasks = useMemo(() => (sprint?.subtasks ?? []).map(task =>
    preview?.id === task.id ? { ...task, status: preview.status, isCompleted: preview.status === 'DONE' } : task
  ), [sprint?.subtasks, preview]);
  const groupedTasks = useMemo(() => ({
    TODO: tasks.filter(task => task.status === 'TODO'),
    IN_PROGRESS: tasks.filter(task => task.status === 'IN_PROGRESS'),
    DONE: tasks.filter(task => task.status === 'DONE'),
  }), [tasks]);
  const activeTask = tasks.find(task => task.id === activeId) ?? null;
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const targetStatus = (over: DragOverEvent['over']): TaskStatus | null => {
    if (!over) return null;
    if (over.data.current?.type === 'Column' && COLUMNS.includes(over.id as TaskStatus)) return over.id as TaskStatus;
    if (over.data.current?.type === 'Task') return tasks.find(task => task.id === over.id)?.status ?? null;
    return null;
  };
  const handleDragStart = ({ active }: DragStartEvent) => {
    if (!canEdit || savingRef.current) return;
    setError(null); setActiveId(Number(active.id)); setPreview(null);
  };
  const handleDragOver = ({ active, over }: DragOverEvent) => {
    if (!canEdit || active.data.current?.type !== 'Task') return;
    const status = targetStatus(over);
    if (status) setPreview(previous => previous?.id === active.id && previous.status === status ? previous : { id: Number(active.id), status });
  };
  const handleDragCancel = () => { setActiveId(null); setPreview(null); };
  const handleDragEnd = async ({ active, over }: DragEndEvent) => {
    setActiveId(null);
    const original = sprint?.subtasks.find(task => task.id === active.id);
    const status = targetStatus(over);
    if (!canEdit || savingRef.current || !original || !status || original.status === status) {
      setPreview(null); return;
    }
    savingRef.current = true; setSaving(true);
    setPreview({ id: original.id, status });
    try {
      await updateSubtaskStatus(original.id, status);
    } catch (err) {
      setError(getErrorMessage(err, 'The task could not be moved. Its previous status has been restored.'));
    } finally {
      savingRef.current = false; setSaving(false); setPreview(null);
    }
  };

  if (loading) return <div className="flex items-center justify-center min-h-[400px] text-slate-400"><p className="text-xs animate-pulse">Loading board data...</p></div>;
  if (!sprint) return (
    <div className="flex flex-col items-center justify-center min-h-[400px] bg-slate-800/10 border border-slate-800/60 rounded-xl p-8 text-center text-slate-500">
      <LayoutTemplate size={36} className="text-slate-700 mb-3" />
      <h3 className="text-sm font-bold text-slate-400">No Active Sprint</h3>
      <p className="text-xs text-slate-600 max-w-sm mt-1">Create and start a sprint in the Backlog tab.</p>
    </div>
  );
  return (
    <div className="flex flex-col h-full w-full">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-100">Board</h2>
        <div className="flex items-center gap-2">
          <button onClick={() => void refreshData()} disabled={saving} className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs border border-slate-700" title="Refresh dashboard state">
            <RotateCcw size={14} /> Sync State
          </button>
          <button onClick={() => setIsReqPanelOpen(value => !value)} className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs border border-slate-700">
            <ListChecks size={14} /> Requirements
          </button>
        </div>
      </div>
      {error && <p role="alert" className="mb-3 text-xs text-red-400">{error}</p>}
      {saving && <p role="status" className="mb-3 text-xs text-slate-400">Saving task status...</p>}
      {isReqPanelOpen && <RequirementsPanel tasks={tasks} />}
      <DndContext sensors={sensors} collisionDetection={closestCorners} onDragStart={handleDragStart}
        onDragOver={handleDragOver} onDragEnd={handleDragEnd} onDragCancel={handleDragCancel}>
        <div className="flex gap-3 overflow-x-auto h-full pb-4 items-start w-full">
          {COLUMNS.map(column => <KanbanColumn key={column} id={column} tasks={groupedTasks[column]} disabled={!canEdit} />)}
        </div>
        <DragOverlay>{activeTask ? <TaskCard task={activeTask} isOverlay disabled /> : null}</DragOverlay>
      </DndContext>
    </div>
  );
};
export default KanbanBoard;
