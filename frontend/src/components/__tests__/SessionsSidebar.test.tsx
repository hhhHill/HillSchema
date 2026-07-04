import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SessionsSidebar from '../SessionsSidebar';

vi.mock('../../api/sessions', () => ({
  inbox: vi.fn().mockResolvedValue([]),
  deleteSession: vi.fn(),
}));

vi.mock('../../api/auth', () => ({
  clearToken: vi.fn(),
  getToken: vi.fn().mockReturnValue('token'),
  isAdmin: vi.fn().mockReturnValue(false),
}));

describe('SessionsSidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uses the approved Chinese copy for the problem-first navigation', async () => {
    render(
      <MemoryRouter initialEntries={['/insights']}>
        <SessionsSidebar refreshKey={0} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /问题/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Chat/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Workspace/i })).not.toBeInTheDocument();
    expect(screen.getByText('先看问题')).toBeInTheDocument();
    expect(screen.getByText(/首页先展示已生成的问题和结论/)).toBeInTheDocument();
  });
});
