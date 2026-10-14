import React from 'react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Clock, AlertCircle } from 'lucide-react';
import { type Subtask, useSprint } from '../../context/SprintContext';
import clsx from 'clsx';

interface Props {
  task: Subtask;
  isOverlay?: boolean;
  disabled?: boolean;
}

const TaskCard: React.FC<Props> = ({ task, isOverlay, disabled }) => {
  const { sprint } = useSprint();
  const {
    setNodeRef,
    attributes,
    listeners,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: task.id,
    data: { type: 'Task', task },
    disabled: disabled || isOverlay,
  });

  const style = {
    transition,
    transform: CSS.Transform.toString(transform),
  };

  // Compute time in column dynamically
  const statusChangedAt = task.statusChangedAt;
  const now = new Date().getTime();
  const changedAt = statusChangedAt ? new Date(statusChangedAt).getTime() : now;
  const timeInColumn = Number.isFinite(changedAt) ? Math.max(0, Math.round((now - changedAt) / (1000 * 60 * 60))) : 0;

  const isBottleneck = task.status !== 'DONE' && timeInColumn > 48;
  const requirement = sprint?.requirements.find(r => r.id === task.requirementId);
  const assignee = task.assignee;

  if (isDragging && !isOverlay) {
    return (
      <div
        ref={setNodeRef}
        style={style}
        className="h-[100px] rounded-lg border-2 border-dashed border-indigo-500/40 bg-indigo-500/5"
      />
    );
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      className={clsx(
        'bg-[#1E293B] rounded-lg border group cursor-grab active:cursor-grabbing hover:border-slate-650 transition-all duration-150 relative overflow-hidden',
        isOverlay
          ? 'shadow-2xl shadow-indigo-500/20 border-indigo-500 scale-[1.03]'
          : 'border-slate-700/60 shadow-sm',
      )}
    >
      {/* Requirement stripe */}
      <div
        className="absolute left-0 top-0 bottom-0 w-1"
        style={{ backgroundColor: isBottleneck ? '#ef4444' : (requirement?.color ?? '#6366F1') }}
      />

      <div className="p-3 pl-4">
        {/* Requirement label + time badge */}
        <div className="flex items-center justify-between mb-1.5">
          <span
            className="text-[10px] font-semibold uppercase tracking-wider truncate max-w-[130px]"
            style={{ color: requirement?.color ?? '#818cf8' }}
            title={requirement?.title}
          >
            {requirement?.title ?? 'Unlinked'}
          </span>

          {isBottleneck && (
            <span className="flex items-center gap-0.5 text-red-405 bg-red-500/10 px-1.5 py-0.5 rounded text-[10px] font-medium shrink-0" title="Bottleneck">
              <AlertCircle size={10} />
              {timeInColumn}h
            </span>
          )}
          {!isBottleneck && timeInColumn > 0 && (
            <span className="flex items-center gap-0.5 text-slate-500 text-[10px] shrink-0">
              <Clock size={10} />
              {timeInColumn}h
            </span>
          )}
        </div>

        {/* Title */}
        <p className="text-[13px] font-medium text-slate-200 leading-snug mb-3 group-hover:text-indigo-300 transition-colors">
          {task.title}
        </p>

        {/* Footer: assignee + points */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            {assignee ? (
              <>
                <div className="w-5 h-5 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-[8px] font-bold text-white shrink-0" title={assignee.fullName}>
                  {assignee.fullName.charAt(0)}
                </div>
                <span className="text-[10px] text-slate-400 hidden sm:inline">{assignee.fullName.split(' ')[0]}</span>
              </>
            ) : (
              <div className="w-5 h-5 rounded-full bg-slate-700 border border-dashed border-slate-600 flex items-center justify-center text-[9px] text-slate-500">
                ?
              </div>
            )}
          </div>

          <span className="text-[10px] font-semibold text-slate-400 bg-slate-900/60 px-1.5 py-0.5 rounded border border-slate-700/40">
            {task.effortPoints} SP
          </span>
        </div>
      </div>
    </div>
  );
};

export default TaskCard;
