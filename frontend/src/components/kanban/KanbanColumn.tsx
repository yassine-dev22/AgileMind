import React from 'react';
import { useDroppable } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import type { Subtask, TaskStatus } from '../../context/SprintContext';
import TaskCard from './TaskCard';

interface Props {
  id: TaskStatus;
  tasks: Subtask[];
  disabled?: boolean;
}

const COLUMN_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
};

const KanbanColumn: React.FC<Props> = ({ id, tasks, disabled }) => {
  const { setNodeRef, isOver } = useDroppable({
    id,
    data: { type: 'Column', columnId: id },
    disabled,
  });

  const totalPoints = tasks.reduce((sum, t) => sum + t.effortPoints, 0);

  // Subtle color accent per column
  const dotColor: Record<TaskStatus, string> = {
    TODO: 'bg-blue-400',
    IN_PROGRESS: 'bg-amber-400',
    DONE: 'bg-emerald-400',
  };

  return (
    <div className="flex flex-col flex-1 min-w-[250px] bg-slate-850/40 rounded-xl border border-slate-800/60 h-full max-h-full overflow-hidden">
      {/* Column header */}
      <div className="px-3 py-2.5 border-b border-slate-800/60 flex items-center justify-between bg-slate-900/40">
        <div className="flex items-center gap-2">
          <div className={`w-2 h-2 rounded-full ${dotColor[id]}`} />
          <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
            {COLUMN_LABELS[id]}
          </h3>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] font-medium bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded">
            {tasks.length}
          </span>
          <span className="text-[10px] font-medium bg-indigo-500/10 text-indigo-400 px-1.5 py-0.5 rounded">
            {totalPoints} SP
          </span>
        </div>
      </div>

      {/* Droppable area */}
      <div
        ref={setNodeRef}
        className={`flex-1 overflow-y-auto p-2 flex flex-col gap-2 transition-colors ${
          isOver ? 'bg-indigo-500/5' : ''
        }`}
      >
        <SortableContext items={tasks.map(t => t.id)} strategy={verticalListSortingStrategy}>
          {tasks.map(task => (
            <TaskCard key={task.id} task={task} disabled={disabled} />
          ))}
        </SortableContext>

        {tasks.length === 0 && (
          <div className="h-full min-h-[80px] border border-dashed border-slate-700/40 rounded-lg flex items-center justify-center text-slate-650 text-xs">
            Drop here
          </div>
        )}
      </div>
    </div>
  );
};

export default KanbanColumn;
