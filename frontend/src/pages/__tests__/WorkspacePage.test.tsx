import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import WorkspacePage from '../WorkspacePage';
import * as workspaceApi from '../../api/workspace';

vi.mock('../../api/workspace', () => ({
  summary: vi.fn(),
}));

describe('WorkspacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders workspace summary without regressing the page shell', async () => {
    vi.mocked(workspaceApi.summary).mockResolvedValue({
      agentId: 'data-agent',
      workspacePath: 'D:/HillSchema/.agentscope/workspace',
      exists: true,
      agentsMdExists: true,
      memoryMdExists: true,
      skillCount: 2,
      subagentCount: 1,
      dailyMemoryCount: 3,
    });

    render(
      <MemoryRouter initialEntries={['/workspace']}>
        <Routes>
          <Route path="/workspace" element={<WorkspacePage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Workspace')).toBeInTheDocument();
    expect(screen.getByText(/Read-only view\./i)).toBeInTheDocument();
    await waitFor(() => {
      expect(workspaceApi.summary).toHaveBeenCalledWith('data-agent');
    });
    expect(await screen.findByText('D:/HillSchema/.agentscope/workspace')).toBeInTheDocument();
  });
});
