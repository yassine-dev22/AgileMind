import React, { useState, useMemo } from 'react';
import { useSprint } from '../context/SprintContext';
import type { Subtask, TaskStatus } from '../context/SprintContext';
import { useAuth } from '../context/AuthContext';
import { getErrorMessage } from '../api/axios';
import { buildBurndownData } from '../utils/metrics';
import {
  Plus,
  ArrowRight,
  TrendingDown,
  BarChart4,
  AlertTriangle,
  Zap,
  Users,
  ChevronRight,
  ChevronDown,
  Calendar,
  Sparkles,
  Mail,
  UserPlus,
  Play,
  LogOut,
  FolderOpen
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  Cell,
  Legend
} from 'recharts';
import KanbanBoard from '../components/kanban/KanbanBoard';

export const Dashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const {
    ownedProjects,
    memberProjects,
    currentProject,
    setCurrentProject,
    sprint,
    loading,
    error,
    members,
    allProjectRequirements,
    refreshData,
    updateSubtaskStatus,
    updateSubtaskStatuses,
    addNewRequirement,
    addNewSubtask,
    createNewSprint,
    completeSprint,
    createProject,
    inviteMember
  } = useSprint();

  // Navigation tabs state for View 2
  const [activeTab, setActiveTab] = useState<'A' | 'B' | 'C' | 'D'>('A');

  // Modal / Form states
  const [isCreateProjOpen, setIsCreateProjOpen] = useState(false);
  const [newProjName, setNewProjName] = useState('');
  const [newProjDesc, setNewProjDesc] = useState('');
  const [isCreatingProj, setIsCreatingProj] = useState(false);

  // Tab B forms
  const [newReqTitle, setNewReqTitle] = useState('');
  const [newReqDesc, setNewReqDesc] = useState('');
  const [newReqColor, setNewReqColor] = useState('#6366F1');
  const [isAddingReq, setIsAddingReq] = useState(false);
  const [isReqFormOpen, setIsReqFormOpen] = useState(false);
  const [operationError, setOperationError] = useState<string | null>(null);
  const [isUpdatingTasks, setIsUpdatingTasks] = useState(false);
  const [isCompletingSprint, setIsCompletingSprint] = useState(false);

  const [activeReqForSubtask, setActiveReqForSubtask] = useState<number | null>(null);
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');
  const [newSubtaskSP, setNewSubtaskSP] = useState(3);
  const [newSubtaskAssignee, setNewSubtaskAssignee] = useState<number | ''>('');
  const [isAddingSubtask, setIsAddingSubtask] = useState(false);

  // Sprint creation form
  const [sprintObjective, setSprintObjective] = useState('');
  const [sprintStart, setSprintStart] = useState('');
  const [sprintEnd, setSprintEnd] = useState('');
  const [selectedReqIds, setSelectedReqIds] = useState<number[]>([]);
  const [isCreatingSprint, setIsCreatingSprint] = useState(false);

  // Invite member form
  const [inviteEmail, setInviteEmail] = useState('');
  const [isInviting, setIsInviting] = useState(false);
  const [inviteMsg, setInviteMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Expanded Requirements in Tab B tree
  const [expandedReqs, setExpandedReqs] = useState<Record<number, boolean>>({});

  // Expanded Contributors in Tab D tree
  const [expandedMembers, setExpandedMembers] = useState<Record<number, boolean>>({});

  const toggleReqExpand = (id: number) => {
    setExpandedReqs(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newProjName.trim() || !newProjDesc.trim()) return;
    if (isCreatingProj) return;
    setOperationError(null);
    setIsCreatingProj(true);
    try {
      await createProject(newProjName.trim(), newProjDesc.trim());
      setNewProjName('');
      setNewProjDesc('');
      setIsCreateProjOpen(false);
    } catch (err) {
      setOperationError(getErrorMessage(err, 'Failed to create the project.'));
    } finally {
      setIsCreatingProj(false);
    }
  };

  const handleAddRequirement = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newReqTitle.trim()) return;
    if (isAddingReq) return;
    setOperationError(null);
    setIsAddingReq(true);
    try {
      await addNewRequirement(newReqTitle.trim(), newReqDesc.trim(), newReqColor);
      setNewReqTitle('');
      setNewReqDesc('');
      setNewReqColor('#6366F1');
      setIsReqFormOpen(false);
    } catch (err) {
      setOperationError(getErrorMessage(err, 'Failed to add the requirement.'));
    } finally {
      setIsAddingReq(false);
    }
  };

  const handleAddSubtask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSubtaskTitle.trim() || activeReqForSubtask === null) return;
    if (isAddingSubtask) return;
    setOperationError(null);
    setIsAddingSubtask(true);
    try {
      await addNewSubtask(
        activeReqForSubtask,
        newSubtaskTitle.trim(),
        newSubtaskSP,
        newSubtaskAssignee ? Number(newSubtaskAssignee) : null
      );
      setNewSubtaskTitle('');
      setNewSubtaskSP(3);
      setNewSubtaskAssignee('');
      setActiveReqForSubtask(null);
    } catch (err) {
      setOperationError(getErrorMessage(err, 'Failed to add the subtask.'));
    } finally {
      setIsAddingSubtask(false);
    }
  };

  const handleInitializeSprint = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sprintObjective.trim() || !sprintStart || !sprintEnd || selectedReqIds.length === 0) return;
    if (isCreatingSprint) return;
    setOperationError(null);
    if (sprintEnd < sprintStart) { setOperationError('Sprint end date must be on or after its start date.'); return; }
    setIsCreatingSprint(true);
    // Calculate total SP of selected requirements
    const totalSelectedSP = allProjectRequirements
      .filter(r => selectedReqIds.includes(r.id))
      .reduce((sum, r) => sum + (r.tasks?.reduce((s, t) => s + t.effortPoints, 0) || 0), 0);

    try {
      await createNewSprint(
        sprintObjective.trim(),
        sprintStart,
        sprintEnd,
        totalSelectedSP,
        selectedReqIds
      );
      setSprintObjective('');
      setSprintStart('');
      setSprintEnd('');
      setSelectedReqIds([]);
      setActiveTab('A'); // Switch back to board
    } catch (err) {
      setOperationError(getErrorMessage(err, 'Failed to initialize the sprint.'));
    } finally {
      setIsCreatingSprint(false);
    }
  };

  const handleInviteMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    if (isInviting) return;
    setIsInviting(true);
    setInviteMsg(null);
    try {
      await inviteMember(inviteEmail.trim());
      setInviteMsg({ text: `Successfully invited ${inviteEmail}!`, type: 'success' });
      setInviteEmail('');
    } catch (err) {
      setInviteMsg({
        text: getErrorMessage(err, 'Failed to invite member. Please check if the user is registered.'),
        type: 'error'
      });
    } finally {
      setIsInviting(false);
    }
  };

  // Toggle checklist status of task from Tab B backlog tree
  const handleToggleSubtaskCheckbox = async (subtaskId: number, currentStatus: string) => {
    if (isUpdatingTasks) return;
    setIsUpdatingTasks(true); setOperationError(null);
    const nextStatus = currentStatus === 'DONE' ? 'TODO' : 'DONE';
    try { await updateSubtaskStatus(subtaskId, nextStatus); }
    catch (err) { setOperationError(getErrorMessage(err, 'Failed to update the task.')); }
    finally { setIsUpdatingTasks(false); }
  };

  // Toggle all subtasks of a requirement from Tab B tree
  const handleToggleRequirementCheckbox = async (_reqId: number, reqTasks: Subtask[]) => {
    if (reqTasks.length === 0 || isUpdatingTasks) return;
    setIsUpdatingTasks(true); setOperationError(null);
    const allDone = reqTasks.every(t => t.status === 'DONE');
    const targetStatus = allDone ? 'TODO' : 'DONE';
    try {
      await updateSubtaskStatuses(reqTasks.filter(task => task.status !== targetStatus).map(task => task.id), targetStatus);
    } catch (err) { setOperationError(getErrorMessage(err, 'Some tasks could not be updated. The board shows the saved statuses.')); }
    finally { setIsUpdatingTasks(false); }
  };

  const handleCompleteSprint = async () => {
    if (!sprint || isCompletingSprint) return;
    setIsCompletingSprint(true); setOperationError(null);
    try { await completeSprint(sprint.id); }
    catch (err) { setOperationError(getErrorMessage(err, 'Failed to conclude the sprint.')); }
    finally { setIsCompletingSprint(false); }
  };

  // Burn-down calculations for Tab C
  const burndownData = useMemo(() => buildBurndownData(sprint), [sprint]);

  // Point Distribution Calculations
  const distributionData = useMemo(() => {
    if (!sprint) return [];
    const todo = sprint.subtasks.filter(t => t.status === 'TODO').reduce((s, t) => s + t.effortPoints, 0);
    const ip = sprint.subtasks.filter(t => t.status === 'IN_PROGRESS').reduce((s, t) => s + t.effortPoints, 0);
    const done = sprint.subtasks.filter(t => t.status === 'DONE').reduce((s, t) => s + t.effortPoints, 0);

    return [
      { name: 'To Do', points: todo, color: '#3B82F6' },
      { name: 'In Progress', points: ip, color: '#F59E0B' },
      { name: 'Done', points: done, color: '#10B981' }
    ];
  }, [sprint]);

  // Render View 1: Homepage Dashboard
  if (!currentProject) {
    return (
      <div className="max-w-7xl mx-auto space-y-10 animate-fade-in">
        {(operationError || error) && <p role="alert" className="text-sm text-red-400">{operationError || error}</p>}
        {/* Profile Card Header */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 md:p-8 flex flex-col md:flex-row md:items-center justify-between gap-6 relative overflow-hidden shadow-xl">
          <div className="absolute top-0 right-0 w-80 h-80 bg-indigo-500/10 blur-[100px] rounded-full pointer-events-none"></div>
          <div className="absolute bottom-0 left-0 w-80 h-80 bg-purple-500/10 blur-[100px] rounded-full pointer-events-none"></div>
          
          <div className="flex items-center gap-4 relative z-10">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-xl font-bold text-white shadow-lg shadow-indigo-500/20">
              {user?.fullName.charAt(0)}
            </div>
            <div>
              <h1 className="text-xl font-extrabold text-slate-100">{user?.fullName}</h1>
              <p className="text-xs text-slate-400 mt-0.5">{user?.email}</p>
              <div className="flex items-center gap-1.5 mt-2">
                <span className="text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full uppercase">
                  {user?.role}
                </span>
                <span className="text-[10px] font-semibold bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full">
                  Capacity: {user?.capacityPoints} SP
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3 relative z-10">
            <button
              onClick={() => setIsCreateProjOpen(true)}
              className="bg-indigo-650 hover:bg-indigo-600 text-white rounded-xl px-4 py-2.5 text-xs font-semibold transition-all shadow-lg shadow-indigo-500/25 flex items-center gap-2"
            >
              <Plus size={16} />
              Initialize Project Workspace
            </button>
            <button
              onClick={logout}
              className="bg-slate-800 hover:bg-slate-700/80 text-slate-300 rounded-xl px-4 py-2.5 text-xs font-semibold transition-all border border-slate-700/40 flex items-center gap-2"
            >
              <LogOut size={16} />
              Sign Out
            </button>
          </div>
        </div>

        {/* Workspace grids */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Owner Grid */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold text-slate-300 flex items-center gap-2">
                <FolderOpen size={16} className="text-indigo-400" />
                My Projects (Owner)
              </h2>
              <span className="text-[10px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full font-medium">
                {ownedProjects.length} Created
              </span>
            </div>

            <div className="grid grid-cols-1 gap-4">
              {ownedProjects.map(p => (
                <div
                  key={p.id}
                  onClick={() => setCurrentProject(p)}
                  className="bg-[#1E293B] border border-slate-700/60 rounded-xl p-5 hover:border-indigo-500/50 hover:shadow-lg hover:shadow-indigo-500/5 cursor-pointer transition-all group relative overflow-hidden"
                >
                  <div className="absolute top-0 right-0 w-24 h-24 bg-indigo-500/5 blur-[25px] rounded-full"></div>
                  <div className="flex justify-between items-start gap-4">
                    <div className="min-w-0">
                      <h3 className="text-sm font-bold text-slate-200 group-hover:text-indigo-300 transition-colors truncate">
                        {p.name}
                      </h3>
                      <p className="text-xs text-slate-400 mt-1 line-clamp-2">
                        {p.description}
                      </p>
                    </div>
                    <span className="text-[9px] font-bold bg-indigo-550/15 text-indigo-400 border border-indigo-500/25 px-2 py-0.5 rounded-full shrink-0 uppercase tracking-wider">
                      Owner
                    </span>
                  </div>

                  <div className="flex items-center justify-between mt-6 pt-3 border-t border-slate-800/40 text-[10px] text-slate-500">
                    <span className="flex items-center gap-1">
                      <Calendar size={12} />
                      {p.startDate} to {p.endDate}
                    </span>
                    <span className="flex items-center gap-1 text-indigo-400 font-semibold group-hover:translate-x-1 transition-transform">
                      Open Workspace <ArrowRight size={12} />
                    </span>
                  </div>
                </div>
              ))}
              {ownedProjects.length === 0 && (
                <div className="bg-slate-900/20 border border-dashed border-slate-800 rounded-xl p-8 text-center text-slate-500">
                  <p className="text-xs italic">No owned projects yet.</p>
                  <p className="text-[10px] text-slate-600 mt-1">Click the button above to create one.</p>
                </div>
              )}
            </div>
          </div>

          {/* Member Grid */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold text-slate-300 flex items-center gap-2">
                <Users size={16} className="text-emerald-400" />
                Collaborative Projects (Member)
              </h2>
              <span className="text-[10px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2 py-0.5 rounded-full font-medium">
                {memberProjects.length} Joined
              </span>
            </div>

            <div className="grid grid-cols-1 gap-4">
              {memberProjects.map(p => (
                <div
                  key={p.id}
                  onClick={() => setCurrentProject(p)}
                  className="bg-[#1E293B] border border-slate-700/60 rounded-xl p-5 hover:border-emerald-500/50 hover:shadow-lg hover:shadow-emerald-500/5 cursor-pointer transition-all group relative overflow-hidden"
                >
                  <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/5 blur-[25px] rounded-full"></div>
                  <div className="flex justify-between items-start gap-4">
                    <div className="min-w-0">
                      <h3 className="text-sm font-bold text-slate-200 group-hover:text-emerald-300 transition-colors truncate">
                        {p.name}
                      </h3>
                      <p className="text-xs text-slate-400 mt-1 line-clamp-2">
                        {p.description}
                      </p>
                    </div>
                    <span className="text-[9px] font-bold bg-emerald-550/15 text-emerald-400 border border-emerald-500/25 px-2 py-0.5 rounded-full shrink-0 uppercase tracking-wider">
                      Member
                    </span>
                  </div>

                  <div className="flex items-center justify-between mt-6 pt-3 border-t border-slate-800/40 text-[10px] text-slate-500">
                    <span className="flex items-center gap-1">
                      <Calendar size={12} />
                      {p.startDate} to {p.endDate}
                    </span>
                    <span className="flex items-center gap-1 text-emerald-400 font-semibold group-hover:translate-x-1 transition-transform">
                      Open Workspace <ArrowRight size={12} />
                    </span>
                  </div>
                </div>
              ))}
              {memberProjects.length === 0 && (
                <div className="bg-slate-900/20 border border-dashed border-slate-800 rounded-xl p-8 text-center text-slate-500">
                  <p className="text-xs italic">No collaborative projects yet.</p>
                  <p className="text-[10px] text-slate-600 mt-1">You will see projects here once invited by email.</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Create Project Modal */}
        {isCreateProjOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl w-full max-w-md overflow-hidden flex flex-col animate-scale-in">
              <div className="flex justify-between items-center px-6 py-4 border-b border-slate-800 bg-slate-800/20">
                <h3 className="text-base font-bold text-slate-200">Initialize Project Workspace</h3>
                <button
                  onClick={() => setIsCreateProjOpen(false)}
                  className="text-slate-400 hover:text-slate-200 transition-colors"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={handleCreateProject} className="p-6 space-y-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Project Name</label>
                  <input
                    type="text"
                    required
                    value={newProjName}
                    onChange={e => setNewProjName(e.target.value)}
                    placeholder="e.g. Apollo Payment Gateway"
                    className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Description</label>
                  <textarea
                    required
                    rows={4}
                    value={newProjDesc}
                    onChange={e => setNewProjDesc(e.target.value)}
                    placeholder="Provide description of objectives and timeline..."
                    className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 resize-none"
                  />
                </div>

                <div className="pt-4 flex justify-end gap-3 border-t border-slate-800">
                  <button
                    type="button"
                    onClick={() => setIsCreateProjOpen(false)}
                    className="px-4 py-2 text-xs font-medium text-slate-400 hover:bg-slate-800 rounded-lg transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isCreatingProj}
                    className="bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg px-4 py-2 text-xs font-semibold transition-all"
                  >
                    {isCreatingProj ? 'Creating...' : 'Initialize'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Determine user role relative to current project
  const isProjOwner = user?.role !== 'VIEWER' && currentProject.owner?.id === user?.id;
  const canEditTasks = user?.role !== 'VIEWER' && !isUpdatingTasks;

  // Render View 2: Project Workspace View
  return (
    <div className="max-w-7xl mx-auto space-y-6 animate-fade-in">
      {(operationError || error) && <p role="alert" className="text-sm text-red-400">{operationError || error}</p>}
      {/* Workspace Header */}
      <div className="bg-[#1E293B] border border-slate-700/60 rounded-2xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-md">
        <div className="space-y-1">
          <button
            onClick={() => setCurrentProject(null)}
            className="text-xs font-bold text-indigo-400 hover:text-indigo-300 transition-colors flex items-center gap-1.5 mb-2 group"
          >
            <span className="group-hover:-translate-x-1 transition-transform">←</span> Back to Homepage
          </button>
          <div className="flex items-center gap-3">
            <h1 className="text-lg font-extrabold text-slate-100">{currentProject.name}</h1>
            <span className="text-[10px] font-bold bg-slate-900/60 text-slate-400 px-2 py-0.5 rounded border border-slate-800 uppercase tracking-wider">
              {isProjOwner ? 'OWNER' : 'MEMBER'}
            </span>
            <button
              onClick={refreshData}
              className="text-[10px] font-bold text-slate-400 hover:text-slate-200 bg-slate-900/40 border border-slate-850 px-2 py-0.5 rounded transition-all"
              title="Refresh Workspace State"
            >
              Sync
            </button>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl">{currentProject.description}</p>
        </div>

        {/* Tab switch buttons */}
        <div className="flex items-center bg-slate-900 p-1 rounded-xl border border-slate-800 self-start md:self-center">
          <button
            onClick={() => setActiveTab('A')}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'A' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Sprint Board
          </button>
          <button
            onClick={() => setActiveTab('B')}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'B' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Backlog
          </button>
          <button
            onClick={() => setActiveTab('C')}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'C' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Visual Metrics
          </button>
          <button
            onClick={() => setActiveTab('D')}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'D' ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Team
          </button>
        </div>
      </div>

      {/* Tab Contents */}
      {loading ? (
        <div className="flex justify-center items-center py-20">
          <div className="text-xs text-slate-500 animate-pulse">Syncing Workspace State...</div>
        </div>
      ) : (
        <div className="min-h-[500px]">
          {/* TAB A: Kanban Board */}
          {activeTab === 'A' && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-sm">
              <KanbanBoard />
            </div>
          )}

          {/* TAB B: Backlog & Sprint Initializer */}
          {activeTab === 'B' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              {/* Backlog Tree View */}
              <div className="lg:col-span-2 space-y-6">
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                  <div className="flex justify-between items-center">
                    <h3 className="text-sm font-bold text-slate-200">Requirements Backlog Tree</h3>
                    {isProjOwner && (
                      <button
                        onClick={() => setIsReqFormOpen(true)}
                        className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
                      >
                        <Plus size={14} /> Add Requirement
                      </button>
                    )}
                  </div>

                  {/* Add Requirement Form inline overlay */}
                  {isReqFormOpen && (
                    <form onSubmit={handleAddRequirement} className="bg-[#1E293B] border border-slate-700/60 p-4 rounded-xl space-y-3 animate-fade-in">
                      <div className="flex justify-between items-center">
                        <span className="text-xs font-bold text-slate-300">Add New Requirement (Epic)</span>
                        <button type="button" onClick={() => setIsReqFormOpen(false)} className="text-slate-400 text-xs">✕</button>
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Title</label>
                          <input
                            type="text"
                            required
                            value={newReqTitle}
                            onChange={e => setNewReqTitle(e.target.value)}
                            placeholder="e.g. Stripe Integration"
                            className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2 outline-none"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Color Label</label>
                          <select
                            value={newReqColor}
                            onChange={e => setNewReqColor(e.target.value)}
                            className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2 outline-none cursor-pointer"
                          >
                            <option value="#6366F1">Indigo</option>
                            <option value="#10B981">Green</option>
                            <option value="#F59E0B">Amber</option>
                            <option value="#EF4444">Red</option>
                            <option value="#EC4899">Pink</option>
                          </select>
                        </div>
                        <div className="space-y-1 md:col-span-2">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Description</label>
                          <input
                            type="text"
                            value={newReqDesc}
                            onChange={e => setNewReqDesc(e.target.value)}
                            placeholder="Requirement notes..."
                            className="w-full bg-slate-950 border border-slate-700 text-slate-100 text-xs rounded-lg p-2 outline-none"
                          />
                        </div>
                      </div>
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setIsReqFormOpen(false)}
                          className="px-3 py-1.5 text-[10px] text-slate-400 hover:bg-slate-800 rounded transition-colors"
                        >
                          Cancel
                        </button>
                        <button
                          type="submit"
                          disabled={isAddingReq}
                          className="bg-indigo-600 hover:bg-indigo-500 text-white rounded px-3 py-1.5 text-[10px] font-semibold"
                        >
                          Save Epic
                        </button>
                      </div>
                    </form>
                  )}

                  {/* Backlog Tree Items */}
                  <div className="space-y-3">
                    {allProjectRequirements.map(req => {
                      const reqTasks = req.tasks || [];
                      const isExpanded = expandedReqs[req.id];
                      const allDone = reqTasks.length > 0 && reqTasks.every(t => t.status === 'DONE');
                      
                      return (
                        <div key={req.id} className="border border-slate-800/80 rounded-xl overflow-hidden">
                          {/* Requirement Row */}
                          <div className="bg-[#1E293B]/40 px-4 py-3 flex items-center justify-between gap-4">
                            <div className="flex items-center gap-3 min-w-0">
                              <input
                                type="checkbox"
                                checked={allDone}
                                disabled={!canEditTasks || (!!req.sprintId && req.sprintId !== sprint?.id)}
                                onChange={() => handleToggleRequirementCheckbox(req.id, reqTasks)}
                                className="rounded border-slate-700 text-indigo-600 focus:ring-indigo-500 cursor-pointer w-4 h-4 bg-slate-950 shrink-0"
                              />
                              <button
                                onClick={() => toggleReqExpand(req.id)}
                                className="text-slate-400 hover:text-slate-200 transition-colors"
                              >
                                {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                              <div
                                className="w-1.5 h-6 rounded shrink-0"
                                style={{ backgroundColor: req.color }}
                              />
                              <div className="min-w-0">
                                <h4 className="text-xs font-bold text-slate-200 truncate">{req.title}</h4>
                                <p className="text-[10px] text-slate-500 truncate">{req.description || 'No description'}</p>
                              </div>
                            </div>

                            <div className="flex items-center gap-3 shrink-0">
                              {req.sprintId ? (
                                <span className="text-[9px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full font-medium">
                                  Sprint Scope
                                </span>
                              ) : (
                                <span className="text-[9px] bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full font-medium">
                                  Backlog
                                </span>
                              )}
                              {isProjOwner && (!req.sprintId || req.sprintId === sprint?.id) && (
                                <button
                                  onClick={() => setActiveReqForSubtask(req.id)}
                                  className="text-[10px] text-indigo-400 hover:text-indigo-300 font-semibold"
                                >
                                  + Subtask
                                </button>
                              )}
                            </div>
                          </div>

                          {/* Subtasks Expanded List */}
                          {isExpanded && (
                            <div className="bg-slate-950/40 divide-y divide-slate-900 border-t border-slate-850 px-4 py-1">
                              {reqTasks.map(task => (
                                <div key={task.id} className="py-2.5 flex items-center justify-between gap-4 text-xs">
                                  <div className="flex items-center gap-3 min-w-0">
                                    <input
                                      type="checkbox"
                                      checked={task.status === 'DONE'}
                                      disabled={!canEditTasks || (!!req.sprintId && req.sprintId !== sprint?.id)}
                                      onChange={() => handleToggleSubtaskCheckbox(task.id, task.status)}
                                      className="rounded border-slate-700 text-indigo-600 focus:ring-indigo-500 cursor-pointer w-3.5 h-3.5 bg-slate-950 shrink-0"
                                    />
                                    <span className={`text-[11px] font-medium truncate ${
                                      task.status === 'DONE' ? 'text-slate-550 line-through' : 'text-slate-300'
                                    }`}>
                                      {task.title}
                                    </span>
                                  </div>

                                  <div className="flex items-center gap-4 shrink-0 text-[10px]">
                                    {task.assignee ? (
                                      <span className="text-slate-400 font-medium bg-slate-900 px-2 py-0.5 rounded">
                                        {task.assignee.fullName}
                                      </span>
                                    ) : (
                                      <span className="text-slate-600 italic">Unassigned</span>
                                    )}
                                    <span className="text-slate-450 font-bold bg-slate-900/60 px-1.5 py-0.5 rounded border border-slate-800">
                                      {task.effortPoints} SP
                                    </span>
                                  </div>
                                </div>
                              ))}
                              {reqTasks.length === 0 && (
                                <p className="text-[10px] text-slate-600 italic py-3 text-center">No subtasks defined.</p>
                              )}
                            </div>
                          )}
                        </div>
                      );
                    })}

                    {allProjectRequirements.length === 0 && (
                      <p className="text-xs text-slate-500 italic text-center py-6">No requirements created yet.</p>
                    )}
                  </div>
                </div>
              </div>

              {/* Sprint Initializer & Subtask Modals */}
              <div className="space-y-6">
                {/* Sprint Initializer Form */}
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                  <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                    <Play size={16} className="text-indigo-400" />
                    Initialize Sprint
                  </h3>

                  {sprint ? (
                    <div className="bg-red-500/10 border border-red-500/20 p-4 rounded-xl space-y-2">
                      <div className="flex items-center gap-2 text-red-400 text-xs font-bold">
                        <AlertTriangle size={16} />
                        Active Sprint Running
                      </div>
                      <p className="text-[11px] text-slate-400 leading-relaxed">
                        Sprint <strong>"{sprint.name}"</strong> is currently active. You must conclude this active sprint inside the board or projects workspace before you can initialize a new one.
                      </p>
                      {isProjOwner && (
                        <button
                          onClick={handleCompleteSprint}
                          disabled={isCompletingSprint || sprint.subtasks.some(task => task.status !== 'DONE')}
                          title={sprint.subtasks.some(task => task.status !== 'DONE') ? 'Complete all sprint tasks before concluding the sprint.' : undefined}
                          className="w-full mt-2 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 py-2 rounded-lg text-xs font-semibold transition-colors"
                        >
                          Conclude Active Sprint
                        </button>
                      )}
                    </div>
                  ) : !isProjOwner ? (
                    <p className="text-xs text-slate-500 italic">
                      Sprint initialization is restricted to the Project Owner.
                    </p>
                  ) : (
                    <form onSubmit={handleInitializeSprint} className="space-y-4">
                      <div className="space-y-1">
                        <label className="text-[10px] text-slate-400 font-semibold uppercase">Sprint Objective</label>
                        <input
                          type="text"
                          required
                          value={sprintObjective}
                          onChange={e => setSprintObjective(e.target.value)}
                          placeholder="e.g. Beta MVP Payments Flow"
                          className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500"
                        />
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Start Date</label>
                          <input
                            type="date"
                            required
                            value={sprintStart}
                            onChange={e => setSprintStart(e.target.value)}
                            className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2 outline-none focus:border-indigo-500 cursor-pointer"
                          />
                        </div>
                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">End Date</label>
                          <input
                            type="date"
                            required
                            value={sprintEnd}
                            min={sprintStart || undefined}
                            onChange={e => setSprintEnd(e.target.value)}
                            className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2 outline-none focus:border-indigo-500 cursor-pointer"
                          />
                        </div>
                      </div>

                      {/* Scope Selector: Backlog requirements check boxes */}
                      <div className="space-y-2">
                        <label className="text-[10px] text-slate-400 font-semibold uppercase block">Select Sprint Scope</label>
                        <div className="max-h-40 overflow-y-auto border border-slate-800 rounded-lg p-2.5 bg-slate-950 space-y-2">
                          {allProjectRequirements
                            .filter(r => !r.sprintId)
                            .map(r => (
                              <label key={r.id} className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                                <input
                                  type="checkbox"
                                  checked={selectedReqIds.includes(r.id)}
                                  onChange={e => {
                                    if (e.target.checked) {
                                      setSelectedReqIds(prev => [...prev, r.id]);
                                    } else {
                                      setSelectedReqIds(prev => prev.filter(id => id !== r.id));
                                    }
                                  }}
                                  className="rounded border-slate-700 text-indigo-600 focus:ring-indigo-500"
                                />
                                <span className="truncate">{r.title}</span>
                              </label>
                            ))}
                          {allProjectRequirements.filter(r => !r.sprintId).length === 0 && (
                            <p className="text-[10px] text-slate-650 italic text-center py-2">No backlog requirements available.</p>
                          )}
                        </div>
                      </div>

                      <button
                        type="submit"
                        disabled={isCreatingSprint || selectedReqIds.length === 0}
                        className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-lg py-2.5 text-xs font-semibold transition-all shadow-md shadow-indigo-500/10"
                      >
                        {isCreatingSprint ? 'Initializing...' : 'Initialize Sprint'}
                      </button>
                    </form>
                  )}
                </div>

                {/* Subtask Creation Form inside inline block */}
                {activeReqForSubtask !== null && (
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 animate-fade-in">
                    <div className="flex justify-between items-center">
                      <h3 className="text-sm font-bold text-slate-200">Add Subtask</h3>
                      <button type="button" onClick={() => setActiveReqForSubtask(null)} className="text-slate-400 text-xs">✕</button>
                    </div>

                    <form onSubmit={handleAddSubtask} className="space-y-4">
                      <div className="space-y-1">
                        <label className="text-[10px] text-slate-400 font-semibold uppercase">Subtask Title</label>
                        <input
                          type="text"
                          required
                          value={newSubtaskTitle}
                          onChange={e => setNewSubtaskTitle(e.target.value)}
                          placeholder="e.g. Write integration test suites"
                          className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500"
                        />
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Story Points</label>
                          <input
                            type="number"
                            required
                            min={1}
                            value={newSubtaskSP}
                            onChange={e => setNewSubtaskSP(parseInt(e.target.value) || 1)}
                            className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 text-center"
                          />
                        </div>

                        <div className="space-y-1">
                          <label className="text-[10px] text-slate-400 font-semibold uppercase">Assignee</label>
                          <select
                            value={newSubtaskAssignee}
                            onChange={e => setNewSubtaskAssignee(e.target.value ? Number(e.target.value) : '')}
                            className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg p-2.5 outline-none focus:border-indigo-500 cursor-pointer"
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

                      <div className="pt-2 flex gap-3">
                        <button
                          type="button"
                          onClick={() => setActiveReqForSubtask(null)}
                          className="w-1/2 border border-slate-800 hover:bg-slate-800 text-slate-400 rounded-lg py-2 text-xs font-semibold transition-colors"
                        >
                          Cancel
                        </button>
                        <button
                          type="submit"
                          disabled={isAddingSubtask}
                          className="w-1/2 bg-indigo-655 hover:bg-indigo-600 text-white rounded-lg py-2 text-xs font-semibold transition-colors"
                        >
                          Add Task
                        </button>
                      </div>
                    </form>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB C: Metrics & AI Insights */}
          {activeTab === 'C' && (
            <div className="space-y-8">
              {sprint ? (
                <>
                  {/* Visual Charts Grid */}
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                    {/* Burn-down Chart */}
                    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-sm">
                      <div className="flex items-center justify-between">
                        <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                          <TrendingDown size={18} className="text-indigo-400" />
                          Sprint Burn-Down Chart
                        </h3>
                        <span className="text-[10px] text-slate-500 font-medium">Trajectory velocity</span>
                      </div>
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={burndownData}>
                            <defs>
                              <linearGradient id="colorIdeal" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#475569" stopOpacity={0.1}/>
                                <stop offset="95%" stopColor="#475569" stopOpacity={0}/>
                              </linearGradient>
                              <linearGradient id="colorActual" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#6366F1" stopOpacity={0.2}/>
                                <stop offset="95%" stopColor="#6366F1" stopOpacity={0}/>
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                            <XAxis dataKey="day" stroke="#475569" fontSize={10} />
                            <YAxis stroke="#475569" fontSize={10} />
                            <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b' }} />
                            <Legend wrapperStyle={{ fontSize: '11px' }} />
                            <Area name="Ideal Trajectory" type="monotone" dataKey="Ideal" stroke="#475569" strokeDasharray="5 5" fillOpacity={1} fill="url(#colorIdeal)" />
                            <Area name="Actual Remaining" type="monotone" dataKey="Actual" stroke="#6366F1" strokeWidth={2} fillOpacity={1} fill="url(#colorActual)" />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    </div>

                    {/* Point Distribution Bar Chart */}
                    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-sm">
                      <div className="flex items-center justify-between">
                        <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                          <BarChart4 size={18} className="text-emerald-400" />
                          Column Point Distribution
                        </h3>
                        <span className="text-[10px] text-slate-500 font-medium">Story Points by status</span>
                      </div>
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={distributionData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                            <XAxis dataKey="name" stroke="#475569" fontSize={10} />
                            <YAxis stroke="#475569" fontSize={10} />
                            <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b' }} />
                            <Bar dataKey="points" name="Story Points" radius={[4, 4, 0, 0]}>
                              {distributionData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={entry.color} />
                              ))}
                            </Bar>
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </div>

                  {/* AI Insights & standup summaries */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    {/* Radial Health Score Gauge */}
                    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col items-center justify-center text-center space-y-4">
                      <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Sprint Health Score</h4>
                      
                      <div className="relative w-40 h-40 flex items-center justify-center">
                        <svg className="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 160 160" aria-hidden="true">
                          <circle cx="80" cy="80" r="70" fill="none" stroke="#1e293b" strokeWidth="10" />
                          <circle cx="80" cy="80" r="70" fill="none" stroke="#6366f1" strokeWidth="10"
                            strokeDasharray={2 * Math.PI * 70}
                            strokeDashoffset={2 * Math.PI * 70 * (1 - Math.max(0, Math.min(100, sprint.healthScore ?? 0)) / 100)} />
                        </svg>
                        <div className="flex flex-col items-center justify-center">
                          <span className="text-4xl font-extrabold text-slate-100">{sprint.healthScore === null ? '—' : `${sprint.healthScore}%`}</span>
                          <span className="text-[10px] text-slate-500 mt-1 uppercase font-semibold">Stability Rating</span>
                        </div>
                      </div>

                      <p className="text-xs text-slate-450 leading-relaxed max-w-[220px]">
                        {sprint.healthScore === null ? 'Health metrics are unavailable. Refresh the workspace to try again.' : sprint.healthScore >= 80
                          ? 'Excellent. Task allocation is balanced, and delivery trajectory looks stable.'
                          : sprint.healthScore >= 50
                          ? 'Moderate Risk. Minor bottlenecks and slight story point variance observed.'
                          : 'High Risk. Significant work delay, resource congestion, or unassigned dependencies.'}
                      </p>
                    </div>

                    {/* AI Alerts & standups */}
                    <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
                      <div className="space-y-4">
                        <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                          <Zap size={18} className="text-indigo-400" />
                          Gemini AI Risk Assessment & standup
                        </h3>

                        {/* List of insights */}
                        <div className="space-y-3">
                          {sprint.insights.map(ins => (
                            <div
                              key={ins.id}
                              className={`p-4 rounded-xl border flex gap-3 items-start ${
                                ins.insightType === 'RISK_ALERT'
                                  ? 'bg-red-500/5 border-red-500/10'
                                  : 'bg-indigo-500/5 border-indigo-500/10'
                              }`}
                            >
                              <div className="mt-0.5">
                                {ins.insightType === 'RISK_ALERT' ? (
                                  <AlertTriangle className="text-red-400 shrink-0" size={16} />
                                ) : (
                                  <Sparkles className="text-indigo-400 shrink-0" size={16} />
                                )}
                              </div>
                              <div className="space-y-1">
                                <h4 className="text-xs font-bold text-slate-200">
                                  {ins.insightType === 'RISK_ALERT' ? 'Sprint Bottleneck Alert' : 'Resource Reassignment Hint'}
                                </h4>
                                <p className="text-xs text-slate-300 leading-relaxed">{ins.explanation}</p>
                              </div>
                            </div>
                          ))}

                          {sprint.insights.length === 0 && (
                            <div className="text-xs text-slate-500 italic p-4 bg-slate-950/60 rounded-xl text-center">
                              {sprint.insightsAvailable ? 'No AI insights have been recorded for this sprint.' : 'AI insights are unavailable. Refresh the workspace to try again.'}
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Automated Standup Summary */}
                      <div className="pt-4 border-t border-slate-800/80 space-y-3">
                        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">n8n Standup Automation Sync</h4>
                        <div className="bg-slate-950 p-4 rounded-xl border border-slate-850 text-xs text-slate-300 leading-relaxed whitespace-pre-wrap font-mono">
                          {sprint.insights.find(insight => insight.automatedStandupSummary)?.automatedStandupSummary || 'No automated standup summary has been received for this sprint.'}
                        </div>
                      </div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-10 text-center text-slate-500 flex flex-col items-center justify-center">
                  <BarChart4 size={36} className="text-slate-700 mb-3 animate-pulse" />
                  <p className="text-xs">No active sprint initialized.</p>
                  <p className="text-[10px] text-slate-600 mt-1">Please navigate to the Backlog tab and start a sprint first.</p>
                </div>
              )}
            </div>
          )}

          {/* TAB D: Team Composition */}
          {activeTab === 'D' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              {/* Member List */}
              <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                <h3 className="text-sm font-bold text-slate-200">Active Workspace Contributors</h3>
                <div className="divide-y divide-slate-800">
                  {members.filter(member => member.role !== 'VIEWER').map(m => {
                    const isExpanded = !!expandedMembers[m.id];
                    
                    // Filter tasks assigned to this member in active sprint and requirements backlog
                    const userTasks: Subtask[] = [];
                    const taskIds = new Set<number>();
                    
                    if (sprint?.subtasks) {
                      sprint.subtasks.forEach(t => {
                        if (t.assignee?.id === m.id) {
                          userTasks.push(t);
                          taskIds.add(t.id);
                        }
                      });
                    }
                    
                    allProjectRequirements?.forEach(r => {
                      r.tasks?.forEach(t => {
                        if (!r.sprintId && (t.assignee?.id === m.id || t.assigneeId === m.id) && !taskIds.has(t.id)) {
                          userTasks.push(t);
                          taskIds.add(t.id);
                        }
                      });
                    });
                    
                    const totalPoints = userTasks.reduce((sum, t) => sum + (t.effortPoints || 0), 0);
                    const doneTasks = userTasks.filter(t => t.status === 'DONE');
                    const progressPercentage = m.capacityPoints > 0 ? Math.round((totalPoints / m.capacityPoints) * 100) : totalPoints > 0 ? 100 : 0;
                    const isOverCapacity = totalPoints > m.capacityPoints;
                    
                    return (
                      <div key={m.id} className="border-b border-slate-800 last:border-b-0 py-2.5">
                        {/* Member Summary Row */}
                        <div
                          onClick={() => setExpandedMembers(prev => ({ ...prev, [m.id]: !prev[m.id] }))}
                          className="flex items-center justify-between gap-4 cursor-pointer hover:bg-slate-800/30 p-2 rounded-xl transition-all"
                        >
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-xs font-bold text-white uppercase shrink-0">
                              {m.fullName.charAt(0)}
                            </div>
                            <div>
                              <h4 className="text-xs font-bold text-slate-200">{m.fullName}</h4>
                              <p className="text-[10px] text-slate-500">{m.email}</p>
                            </div>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="text-[9px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full font-semibold uppercase">
                              {m.role}
                            </span>
                            <span className="text-[9px] bg-slate-850 text-slate-450 px-2 py-0.5 rounded-full font-medium">
                              {totalPoints} / {m.capacityPoints} SP
                            </span>
                            <div className="text-slate-400">
                              {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </div>
                          </div>
                        </div>
                        
                        {/* Accordion Content */}
                        {isExpanded && (
                          <div className="mt-3 pl-12 pr-4 pb-2 space-y-3 animate-fade-in text-xs text-slate-300">
                            {/* Capacity Utilization Progress Bar */}
                            <div className="space-y-1.5">
                              <div className="flex justify-between text-[10px] text-slate-450 font-semibold uppercase">
                                <span>Capacity Utilization</span>
                                <span>{progressPercentage}% ({totalPoints} / {m.capacityPoints} SP)</span>
                              </div>
                              <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden border border-slate-800">
                                <div
                                  className={`h-full rounded-full transition-all duration-500 ${
                                    isOverCapacity
                                      ? 'bg-rose-500'
                                      : progressPercentage > 80
                                      ? 'bg-amber-500'
                                      : 'bg-indigo-500'
                                  }`}
                                  style={{ width: `${Math.min(100, progressPercentage)}%` }}
                                ></div>
                              </div>
                            </div>
                            
                            {/* Task Breakdown stats */}
                            <div className="grid grid-cols-3 gap-2 text-center text-[10px] py-1 bg-slate-950/40 rounded-lg border border-slate-850">
                              <div>
                                <span className="block font-bold text-slate-200">{userTasks.length}</span>
                                <span className="text-slate-500 uppercase font-semibold">Total Tasks</span>
                              </div>
                              <div>
                                <span className="block font-bold text-emerald-400">{doneTasks.length}</span>
                                <span className="text-slate-500 uppercase font-semibold">Completed</span>
                              </div>
                              <div>
                                <span className="block font-bold text-amber-400">{userTasks.filter(t => t.status === 'IN_PROGRESS').length}</span>
                                <span className="text-slate-500 uppercase font-semibold">In Progress</span>
                              </div>
                            </div>
                            
                            {/* Assigned Tasks list */}
                            <div className="space-y-1.5">
                              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Assigned Subtasks</h5>
                              <div className="space-y-1 max-h-48 overflow-y-auto pr-1">
                                {userTasks.map(t => {
                                  const reqTitle = allProjectRequirements.find(r => r.id === t.requirementId)?.title || 'Task';
                                  
                                  const statusBadge: Record<TaskStatus, string> = {
                                    TODO: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
                                    IN_PROGRESS: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
                                    DONE: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
                                  };
                                  
                                  return (
                                    <div key={t.id} className="flex justify-between items-center bg-slate-950/60 hover:bg-slate-950 p-2 rounded-lg border border-slate-850/50">
                                      <div className="min-w-0 pr-2">
                                        <div className="text-[11px] font-medium text-slate-200 truncate">{t.title}</div>
                                        <div className="text-[9px] text-slate-500 truncate">{reqTitle}</div>
                                      </div>
                                      <div className="flex items-center gap-2 shrink-0">
                                        <span className={`text-[8px] font-bold border px-1.5 py-0.5 rounded uppercase tracking-wider ${statusBadge[t.status]}`}>
                                          {t.status.replace('_', ' ')}
                                        </span>
                                        <span className="text-[9px] font-bold bg-slate-900 px-1 py-0.5 rounded border border-slate-800 text-slate-400">
                                          {t.effortPoints} SP
                                        </span>
                                      </div>
                                    </div>
                                  );
                                })}
                                {userTasks.length === 0 && (
                                  <p className="text-[10px] text-slate-500 italic text-center py-2">No tasks assigned to this contributor.</p>
                                )}
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                  {members.filter(member => member.role !== 'VIEWER').length === 0 && (
                    <p className="text-xs text-slate-500 italic text-center py-4">No team contributors added yet.</p>
                  )}
                  {members.filter(member => member.role === 'VIEWER').map(member => (
                    <div key={member.id} className="py-3 text-xs text-slate-400 flex items-center justify-between">
                      <span>{member.fullName} <span className="text-slate-500">({member.email})</span></span>
                      <span className="text-[10px] uppercase">Viewer · read-only access</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Invite contributor form */}
              <div>
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
                  <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                    <UserPlus size={16} className="text-indigo-400" />
                    Invite Collaborator
                  </h3>
                  
                  {isProjOwner ? (
                    <form onSubmit={handleInviteMember} className="space-y-4">
                      {inviteMsg && (
                        <div className={`p-3 rounded-lg text-xs text-center border ${
                          inviteMsg.type === 'success' 
                            ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400' 
                            : 'bg-red-500/10 border-red-500/20 text-red-400'
                        }`}>
                          {inviteMsg.text}
                        </div>
                      )}
                      
                      <div className="space-y-1">
                        <label className="text-[10px] text-slate-400 font-semibold uppercase">Contributor Email</label>
                        <div className="relative">
                          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <Mail size={14} className="text-slate-500" />
                          </div>
                          <input
                            type="email"
                            required
                            value={inviteEmail}
                            onChange={e => setInviteEmail(e.target.value)}
                            placeholder="collaborator@company.com"
                            className="w-full bg-slate-950 border border-slate-750 text-slate-100 text-xs rounded-lg pl-9 pr-3 py-2.5 outline-none focus:border-indigo-500"
                          />
                        </div>
                      </div>

                      <button
                        type="submit"
                        disabled={isInviting}
                        className="w-full bg-indigo-650 hover:bg-indigo-600 text-white rounded-lg py-2.5 text-xs font-semibold transition-all shadow-md shadow-indigo-500/10 flex items-center justify-center gap-2"
                      >
                        <UserPlus size={14} />
                        {isInviting ? 'Inviting...' : 'Invite Member'}
                      </button>
                    </form>
                  ) : (
                    <p className="text-xs text-slate-500 italic">
                      Invitations are restricted to the project owner.
                    </p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default Dashboard;
