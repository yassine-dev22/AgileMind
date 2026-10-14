import React, { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import axios from 'axios';
import api, { getErrorMessage } from '../api/axios';
import { AUTH_TOKEN_KEY, normalizeUser } from '../api/session';
import { useAuth } from './AuthContext';
import type { User } from './AuthContext';
export type { User } from './AuthContext';

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export interface Project {
  id: number; name: string; description: string;
  startDate?: string; endDate?: string; status?: string;
  owner?: User; members?: User[]; requirements?: Requirement[]; sprints?: SprintInfo[];
}
export interface Requirement {
  id: number; title: string; description: string; color: string;
  status: TaskStatus | 'BACKLOG'; storyPoints: number; sprintId?: number | null; tasks?: Subtask[];
}
export interface Subtask {
  id: number; requirementId: number; title: string; description?: string;
  priority: string; status: TaskStatus; isCompleted: boolean; effortPoints: number; estimatedHours: number;
  assigneeId?: number | null; assignee?: User | null; statusChangedAt?: string;
}
export interface AiInsight {
  id: number; explanation: string; confidenceScore: number; acknowledged: boolean;
  insightType: 'RISK_ALERT' | 'REASSIGNMENT_SUGGESTION' | 'RETROSPECTIVE';
  sprintHealthScore: number; riskLevel: string; bottlenecksJson?: string; automatedStandupSummary?: string;
}
export interface SprintInfo {
  sprintId: number; objective: string; startDate: string; endDate: string;
  sprintStatus: 'PLANNED' | 'ACTIVE' | 'DONE'; pointsPlanned: number; isActive: boolean; isConcluded: boolean;
}
export interface SprintData {
  id: number; name: string; objectives: string; healthScore: number | null;
  startDate: string; endDate: string; requirements: Requirement[]; subtasks: Subtask[];
  insights: AiInsight[]; insightsAvailable: boolean; burndownPoints: BurndownPoint[];
  sprintStatus: 'PLANNED' | 'ACTIVE' | 'DONE';
}
export interface BurndownPoint { day: string; remainingPoints: number }

interface RawTask extends Partial<Subtask> { requirement?: { id: number } }
interface RawRequirement extends Omit<Partial<Requirement>, 'tasks'> {
  sprint?: { sprintId: number } | null; tasks?: RawTask[]; subtasks?: RawTask[];
}
interface ProjectDetail { project: Project; activeSprint: SprintInfo | null; activeSprintTasks: RawTask[]; members: User[] }
interface HomeData { ownedProjects: Project[]; memberProjects: Project[] }
interface ProjectMetrics { burndownPoints?: BurndownPoint[] }

interface SprintContextType {
  projects: Project[]; ownedProjects: Project[]; memberProjects: Project[];
  currentProject: Project | null; setCurrentProject: (project: Project | null) => void;
  sprint: SprintData | null; loading: boolean; error: string | null; members: User[];
  allProjectRequirements: Requirement[]; refreshData: () => Promise<void>;
  updateSubtaskStatus: (subtaskId: number, status: TaskStatus) => Promise<void>;
  updateSubtaskStatuses: (subtaskIds: number[], status: TaskStatus) => Promise<void>;
  addNewRequirement: (title: string, description: string, color: string) => Promise<void>;
  addNewSubtask: (requirementId: number, title: string, points: number, assigneeId?: number | null) => Promise<void>;
  createNewSprint: (objective: string, start: string, end: string, points: number, requirementIds: number[]) => Promise<void>;
  completeSprint: (sprintId: number) => Promise<void>;
  createProject: (name: string, description: string) => Promise<Project>;
  inviteMember: (email: string) => Promise<void>;
}
const SprintContext = createContext<SprintContextType | undefined>(undefined);
const PROJECT_KEY = 'levelup_current_project_id';
const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

function mapTask(task: RawTask, requirementId?: number): Subtask {
  const status = TASK_STATUSES.includes(task.status as TaskStatus) ? task.status as TaskStatus : 'TODO';
  return {
    id: task.id!, requirementId: requirementId ?? task.requirementId ?? task.requirement?.id ?? 0,
    title: task.title ?? '', description: task.description ?? '', priority: task.priority ?? 'MEDIUM',
    status, isCompleted: status === 'DONE', effortPoints: task.effortPoints ?? 0,
    estimatedHours: task.estimatedHours ?? 0, assigneeId: task.assigneeId ?? task.assignee?.id ?? null,
    assignee: normalizeUser(task.assignee), statusChangedAt: task.statusChangedAt,
  };
}
function mapRequirement(requirement: RawRequirement): Requirement {
  return {
    id: requirement.id!, title: requirement.title ?? '', description: requirement.description ?? '',
    color: requirement.color ?? '#6366F1', status: requirement.status ?? 'BACKLOG',
    storyPoints: requirement.storyPoints ?? 0, sprintId: requirement.sprintId ?? requirement.sprint?.sprintId ?? null,
    tasks: (requirement.subtasks ?? requirement.tasks ?? []).map(task => mapTask(task, requirement.id)),
  };
}

const WorkspaceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [ownedProjects, setOwnedProjects] = useState<Project[]>([]);
  const [memberProjects, setMemberProjects] = useState<Project[]>([]);
  const [currentProject, setCurrentProjectState] = useState<Project | null>(null);
  const [sprint, setSprint] = useState<SprintData | null>(null);
  const [members, setMembers] = useState<User[]>([]);
  const [allProjectRequirements, setAllProjectRequirements] = useState<Requirement[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectedId = useRef<number | null>(null);
  const selectionVersion = useRef(0);
  const detailRequest = useRef<AbortController | null>(null);
  const homeRequest = useRef<AbortController | null>(null);

  const clearDetails = useCallback(() => {
    detailRequest.current?.abort();
    detailRequest.current = null;
    setSprint(null); setMembers([]); setAllProjectRequirements([]); setLoading(false);
  }, []);

  const loadProjects = useCallback((): Promise<Project[] | null> => {
    if (!isAuthenticated || !user) return Promise.resolve(null);
    homeRequest.current?.abort();
    const controller = new AbortController();
    homeRequest.current = controller;
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    return api.get<HomeData>('/home', { signal: controller.signal }).then(({ data }) => {
      if (controller.signal.aborted || localStorage.getItem(AUTH_TOKEN_KEY) !== token) return null;
      const owned = data.ownedProjects ?? [];
      const member = data.memberProjects ?? [];
      const combined = [...new Map([...owned, ...member].map(project => [project.id, project])).values()];
      setOwnedProjects(owned); setMemberProjects(member); setProjects(combined);
      return combined;
    }).catch((err: unknown) => {
      if (!axios.isCancel(err) && !controller.signal.aborted) setError(getErrorMessage(err, 'Failed to load your projects.'));
      return null;
    });
  }, [isAuthenticated, user]);

  const loadProjectDetail = useCallback(async (projectId: number) => {
    if (selectedId.current !== projectId) return;
    detailRequest.current?.abort();
    const controller = new AbortController();
    detailRequest.current = controller;
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const isCurrent = () => !controller.signal.aborted && detailRequest.current === controller
      && selectedId.current === projectId && localStorage.getItem(AUTH_TOKEN_KEY) === token;
    setLoading(true); setError(null);
    try {
      const [{ data }, { data: requirements }] = await Promise.all([
        api.get<ProjectDetail>(`/projects/${projectId}/detail`, { signal: controller.signal }),
        api.get<RawRequirement[]>(`/projects/${projectId}/requirements`, { signal: controller.signal }),
      ]);
      if (!isCurrent()) return;
      const mappedReqs = requirements.map(mapRequirement);
      let nextSprint: SprintData | null = null;
      if (data.activeSprint) {
        const active = data.activeSprint;
        const [health, insights, metrics] = await Promise.allSettled([
          api.get<{ score: number }>(`/sprints/${active.sprintId}/health`, { signal: controller.signal }),
          api.get<AiInsight[]>(`/ai/sprint/${active.sprintId}`, { signal: controller.signal }),
          api.get<ProjectMetrics>(`/projects/${projectId}/metrics`, { signal: controller.signal }),
        ]);
        if (!isCurrent()) return;
        nextSprint = {
          id: active.sprintId, name: active.objective || `Sprint ${active.sprintId}`, objectives: active.objective ?? '',
          healthScore: health.status === 'fulfilled' ? health.value.data.score : null,
          startDate: active.startDate, endDate: active.endDate,
          requirements: mappedReqs.filter(requirement => requirement.sprintId === active.sprintId),
          subtasks: (data.activeSprintTasks ?? []).map(task => mapTask(task)),
          insights: insights.status === 'fulfilled' ? insights.value.data : [],
          insightsAvailable: insights.status === 'fulfilled',
          burndownPoints: metrics.status === 'fulfilled' ? metrics.value.data.burndownPoints ?? [] : [],
          sprintStatus: active.sprintStatus,
        };
      }
      if (!isCurrent()) return;
      setCurrentProjectState(data.project);
      setAllProjectRequirements(mappedReqs);
      setMembers((data.members ?? []).map(normalizeUser).filter((member): member is User => member !== null));
      setSprint(nextSprint);
    } catch (err) {
      if (isCurrent() && !axios.isCancel(err)) {
        setError(getErrorMessage(err, 'Failed to load the project workspace.'));
        setSprint(null); setMembers([]); setAllProjectRequirements([]);
      }
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, []);

  const setCurrentProject = useCallback((project: Project | null) => {
    selectionVersion.current++;
    selectedId.current = project?.id ?? null;
    clearDetails(); setError(null); setCurrentProjectState(project);
    if (project) {
      localStorage.setItem(PROJECT_KEY, String(project.id));
      void loadProjectDetail(project.id);
    } else localStorage.removeItem(PROJECT_KEY);
  }, [clearDetails, loadProjectDetail]);

  const refreshData = useCallback(() => {
    const version = selectionVersion.current;
    return loadProjects().then(async list => {
      if (!list || version !== selectionVersion.current) return;
      const storedId = localStorage.getItem(PROJECT_KEY);
      const project = list.find(item => String(item.id) === storedId);
      if (project) {
        selectedId.current = project.id;
        setCurrentProjectState(project);
        await loadProjectDetail(project.id);
      } else setCurrentProject(null);
    });
  }, [loadProjects, loadProjectDetail, setCurrentProject]);

  useEffect(() => {
    if (isAuthenticated) void refreshData();
    return () => {
      homeRequest.current?.abort(); detailRequest.current?.abort();
    };
  }, [isAuthenticated, refreshData]);

  const requireProject = useCallback(() => {
    if (!currentProject || selectedId.current !== currentProject.id) throw new Error('Select a project first.');
    return currentProject.id;
  }, [currentProject]);

  const mutateProject = useCallback(async (mutation: () => Promise<unknown>) => {
    const id = requireProject();
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    await mutation();
    if (selectedId.current === id && localStorage.getItem(AUTH_TOKEN_KEY) === token) await loadProjectDetail(id);
  }, [requireProject, loadProjectDetail]);

  const updateSubtaskStatus = useCallback((id: number, status: TaskStatus) => mutateProject(() => api.patch(`/tasks/${id}/status`, { status })), [mutateProject]);
  const updateSubtaskStatuses = useCallback(async (ids: number[], status: TaskStatus) => {
    const projectId = requireProject();
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    try {
      for (const id of ids) await api.patch(`/tasks/${id}/status`, { status });
    } finally {
      // Read once, including when a later write failed after earlier tasks were saved.
      if (selectedId.current === projectId && localStorage.getItem(AUTH_TOKEN_KEY) === token) await loadProjectDetail(projectId);
    }
  }, [requireProject, loadProjectDetail]);
  const addNewRequirement = useCallback((title: string, description: string, color: string) => {
    const id = requireProject();
    if (!title.trim()) return Promise.reject(new Error('A requirement title is required.'));
    return mutateProject(() => api.post(`/projects/${id}/requirements`, { title: title.trim(), description, color }));
  }, [requireProject, mutateProject]);
  const addNewSubtask = useCallback((requirementId: number, title: string, points: number, assigneeId?: number | null) => {
    if (!title.trim() || !Number.isInteger(points) || points < 0) return Promise.reject(new Error('Enter a title and non-negative whole story points.'));
    return mutateProject(() => api.post(`/requirements/${requirementId}/subtasks`, {
      title: title.trim(), priority: 'MEDIUM', status: 'TODO', effortPoints: points,
      estimatedHours: points * 4, assigneeId: assigneeId ?? null,
    }));
  }, [mutateProject]);
  const createNewSprint = useCallback((objective: string, startDate: string, endDate: string, plannedStoryPoints: number, requirementIds: number[]) => {
    const id = requireProject();
    if (!objective.trim() || !startDate || !endDate || endDate < startDate || !requirementIds.length) {
      return Promise.reject(new Error('Enter an objective, ordered sprint dates, and at least one requirement.'));
    }
    return mutateProject(() => api.post(`/projects/${id}/sprints`, {
      sprintObjective: objective.trim(), startDate, endDate, plannedStoryPoints, requirementIds, sprintStatus: 'ACTIVE',
    }));
  }, [requireProject, mutateProject]);
  const completeSprint = useCallback((id: number) => mutateProject(() => api.patch(`/sprints/${id}/complete`)), [mutateProject]);
  const inviteMember = useCallback((email: string) => {
    const id = requireProject();
    return mutateProject(() => api.post(`/projects/${id}/members`, { email: email.trim() }));
  }, [requireProject, mutateProject]);
  const createProject = useCallback(async (name: string, description: string): Promise<Project> => {
    if (!name.trim()) throw new Error('A project name is required.');
    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const version = selectionVersion.current;
    const today = new Date(); const nextYear = new Date(); nextYear.setFullYear(today.getFullYear() + 1);
    const date = (value: Date) => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
    const { data } = await api.post<Project>('/projects', {
      name: name.trim(), description, startDate: date(today), endDate: date(nextYear), status: 'PLANNED',
    });
    if (localStorage.getItem(AUTH_TOKEN_KEY) === token) {
      await loadProjects();
      if (localStorage.getItem(AUTH_TOKEN_KEY) === token && selectionVersion.current === version) setCurrentProject(data);
    }
    return data;
  }, [loadProjects, setCurrentProject]);

  const value = useMemo(() => ({
    projects, ownedProjects, memberProjects, currentProject, setCurrentProject, sprint, loading, error,
    members, allProjectRequirements, refreshData, updateSubtaskStatus, updateSubtaskStatuses, addNewRequirement, addNewSubtask,
    createNewSprint, completeSprint, createProject, inviteMember,
  }), [projects, ownedProjects, memberProjects, currentProject, setCurrentProject, sprint, loading, error,
    members, allProjectRequirements, refreshData, updateSubtaskStatus, updateSubtaskStatuses, addNewRequirement, addNewSubtask,
    createNewSprint, completeSprint, createProject, inviteMember]);
  return <SprintContext.Provider value={value}>{children}</SprintContext.Provider>;
};
// Remounting drops all account-specific state before another session renders it.
export const SprintProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isAuthenticated } = useAuth();
  return <WorkspaceProvider key={isAuthenticated ? user?.id : 'signed-out'}>{children}</WorkspaceProvider>;
};
// eslint-disable-next-line react-refresh/only-export-components -- This hook shares the provider's private context.
export const useSprint = () => {
  const context = useContext(SprintContext);
  if (!context) throw new Error('useSprint must be used within a SprintProvider');
  return context;
};
