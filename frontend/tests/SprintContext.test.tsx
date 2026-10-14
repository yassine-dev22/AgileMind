import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../src/context/AuthContext';
import { SprintProvider, useSprint } from '../src/context/SprintContext';
import api from '../src/api/axios';

vi.mock('../src/api/axios', () => ({
  default: { get: vi.fn(), patch: vi.fn(), post: vi.fn() },
  getErrorMessage: (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback,
}));

const projects = [
  { id: 1, name: 'A', description: 'A' },
  { id: 2, name: 'B', description: 'B' },
];
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
function detail(id: number) {
  return { data: {
    project: projects[id - 1], members: [{ id, email: `${id}@test.dev`, fullName: `Member ${id}`, role: 'MEMBER', capacityPoints: 0 }],
    activeSprint: { sprintId: id + 100, objective: `Sprint ${id}`, startDate: '2026-10-01', endDate: '2026-10-10', sprintStatus: 'ACTIVE' },
    activeSprintTasks: [{ id, title: `Task ${id}`, requirement: { id }, status: 'TODO', effortPoints: 3 }],
  } };
}
function Harness() {
  const workspace = useSprint();
  const { logout } = useAuth();
  return <>
    {workspace.projects.map(project => <button key={project.id} onClick={() => workspace.setCurrentProject(project)}>Pick {project.name}</button>)}
    <button onClick={logout}>Logout</button>
    <button onClick={() => void workspace.updateSubtaskStatus(1, 'DONE')}>Complete task</button>
    <button onClick={() => void workspace.updateSubtaskStatuses([1, 2], 'DONE').catch(() => undefined)}>Complete requirement</button>
    <output data-testid="state">{JSON.stringify({ project: workspace.currentProject?.id, sprint: workspace.sprint?.id,
      tasks: workspace.sprint?.subtasks.map(task => task.title), requirements: workspace.allProjectRequirements.map(req => req.id),
      projects: workspace.projects.length, capacity: workspace.members[0]?.capacityPoints, health: workspace.sprint?.healthScore })}</output>
  </>;
}
function renderWorkspace() {
  render(<AuthProvider><SprintProvider><Harness /></SprintProvider></AuthProvider>);
}
function state() { return JSON.parse(screen.getByTestId('state').textContent!); }

beforeEach(() => {
  localStorage.clear(); vi.clearAllMocks();
  localStorage.setItem('jwt_token', `header.${btoa(JSON.stringify({ exp: Date.now() / 1000 + 3600 }))}.signature`);
  localStorage.setItem('auth_user', JSON.stringify({ id: 10, email: 'user@test.dev', fullName: 'User', role: 'MEMBER', capacityPoints: 40 }));
  vi.mocked(api.get).mockImplementation(async url => {
    if (url === '/home') return { data: { ownedProjects: projects, memberProjects: [projects[0]] } };
    if (url?.endsWith('/requirements')) {
      const id = Number(url.split('/')[2]);
      return { data: [{ id, title: `Requirement ${id}`, sprint: { sprintId: id + 100 } }] };
    }
    if (url?.endsWith('/health')) return { data: { score: 0 } };
    if (url?.startsWith('/ai/')) return { data: [] };
    if (url?.endsWith('/metrics')) return { data: { burndownPoints: [] } };
    const id = Number(url?.split('/')[2]);
    return detail(id);
  });
});
afterEach(() => { cleanup(); localStorage.clear(); });

describe('workspace requests', () => {
  it('ignores older project responses and preserves legitimate zero metrics', async () => {
    const first = deferred<ReturnType<typeof detail>>();
    const second = deferred<ReturnType<typeof detail>>();
    const original = vi.mocked(api.get).getMockImplementation()!;
    vi.mocked(api.get).mockImplementation((url, config) => {
      if (url === '/projects/1/detail') return first.promise;
      if (url === '/projects/2/detail') return second.promise;
      return original(url, config);
    });
    renderWorkspace();
    await screen.findByText('Pick A');
    expect(state().projects).toBe(2);
    fireEvent.click(screen.getByText('Pick A')); fireEvent.click(screen.getByText('Pick B'));
    await act(async () => { second.resolve(detail(2)); });
    await waitFor(() => expect(state().sprint).toBe(102));
    await act(async () => { first.resolve(detail(1)); });
    expect(state()).toMatchObject({ project: 2, sprint: 102, tasks: ['Task 2'], requirements: [2], capacity: 0, health: 0 });
    const firstRequest = vi.mocked(api.get).mock.calls.find(([url]) => url === '/projects/1/detail');
    expect(firstRequest?.[1]?.signal?.aborted).toBe(true);
  });

  it('clears all workspace data at logout and rejects late in-flight results', async () => {
    const pending = deferred<ReturnType<typeof detail>>();
    const original = vi.mocked(api.get).getMockImplementation()!;
    vi.mocked(api.get).mockImplementation((url, config) => url === '/projects/1/detail' ? pending.promise : original(url, config));
    renderWorkspace(); await screen.findByText('Pick A');
    fireEvent.click(screen.getByText('Pick A')); fireEvent.click(screen.getByText('Logout'));
    await act(async () => { pending.resolve(detail(1)); });
    expect(state()).toEqual({ projects: 0, requirements: [] });
  });

  it('does not reload the previous project after a late mutation completes', async () => {
    const pending = deferred<{ data: object }>();
    vi.mocked(api.patch).mockReturnValue(pending.promise);
    renderWorkspace(); await screen.findByText('Pick A');
    fireEvent.click(screen.getByText('Pick A')); await waitFor(() => expect(state().sprint).toBe(101));
    fireEvent.click(screen.getByText('Complete task')); fireEvent.click(screen.getByText('Pick B'));
    await waitFor(() => expect(state().sprint).toBe(102));
    await act(async () => { pending.resolve({ data: {} }); });
    expect(state().project).toBe(2);
    expect(vi.mocked(api.get).mock.calls.filter(([url]) => url === '/projects/1/detail')).toHaveLength(1);
  });

  it('reloads once after a bulk update partially fails so saved statuses remain authoritative', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: {} }).mockRejectedValueOnce(new Error('Conflict'));
    renderWorkspace(); await screen.findByText('Pick A');
    fireEvent.click(screen.getByText('Pick A')); await waitFor(() => expect(state().sprint).toBe(101));
    fireEvent.click(screen.getByText('Complete requirement'));
    await waitFor(() => expect(vi.mocked(api.get).mock.calls.filter(([url]) => url === '/projects/1/detail')).toHaveLength(2));
    expect(vi.mocked(api.patch).mock.calls.map(([url]) => url)).toEqual(['/tasks/1/status', '/tasks/2/status']);
    expect(state().project).toBe(1);
  });
});
