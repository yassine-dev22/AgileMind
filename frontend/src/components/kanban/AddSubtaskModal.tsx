import React, { useState } from 'react';
import { X } from 'lucide-react';
import { useSprint } from '../../context/SprintContext';
import { getErrorMessage } from '../../api/axios';

interface Props {
  onClose: () => void;
}

const AddSubtaskModal: React.FC<Props> = ({ onClose }) => {
  const { sprint, addNewSubtask, members } = useSprint();
  const [requirementId, setRequirementId] = useState<number | ''>(sprint?.requirements[0]?.id || '');
  const [title, setTitle] = useState('');
  const [storyPoints, setStoryPoints] = useState(1);
  const [assigneeId, setAssigneeId] = useState<string>('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!sprint) {
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (requirementId === '' || !title.trim()) return;
    if (saving) return;
    setSaving(true); setError(null);
    
    try {
      await addNewSubtask(
        requirementId,
        title.trim(),
        storyPoints,
        assigneeId ? Number(assigneeId) : null
      );
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to add the subtask.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-700 rounded-xl shadow-2xl w-full max-w-md overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex justify-between items-center px-5 py-3 border-b border-slate-700 bg-slate-800/40">
          <h2 className="text-base font-bold text-slate-100">Add New Subtask</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-100 transition-colors p-1">
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {error && <p role="alert" className="text-xs text-red-400">{error}</p>}
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Parent Requirement (Epic)</label>
            <select
              value={requirementId}
              onChange={e => setRequirementId(Number(e.target.value))}
              className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 cursor-pointer"
              required
            >
              <option value="" disabled>Select Requirement</option>
              {sprint.requirements.map(req => (
                <option key={req.id} value={req.id}>
                  {req.title}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Subtask Title</label>
            <input
              type="text"
              required
              value={title}
              onChange={e => setTitle(e.target.value)}
              className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 transition-colors"
              placeholder="e.g. Build API Endpoints"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Story Points</label>
              <input
                type="number"
                min={0}
                required
                value={storyPoints}
                onChange={e => setStoryPoints(parseInt(e.target.value) || 0)}
                className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 text-center"
              />
            </div>
            
            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Assignee</label>
              <select
                value={assigneeId}
                onChange={e => setAssigneeId(e.target.value)}
                className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 cursor-pointer"
              >
                <option value="">Unassigned</option>
                {members.filter(member => member.role !== 'VIEWER').map(m => (
                  <option key={m.id} value={m.id}>
                    {m.fullName}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Footer */}
          <div className="pt-4 border-t border-slate-800 flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg text-slate-300 hover:bg-slate-800 transition-colors text-xs font-medium"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition-colors shadow-lg shadow-indigo-500/25"
            >
              Add Subtask
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AddSubtaskModal;
