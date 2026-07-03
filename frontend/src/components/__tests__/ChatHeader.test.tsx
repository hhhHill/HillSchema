import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import ChatHeader from '../ChatHeader';

describe('ChatHeader', () => {
  it('does not render a workspace action for editable agents', () => {
    render(
      <MemoryRouter>
        <ChatHeader
          agent={{
            id: 'data-agent',
            name: 'DataAgent',
            description: 'desc',
            scope: 'global',
            createdAt: 0,
            updatedAt: 0,
            tierForCurrentUser: 'EDIT',
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /Insights/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Skills/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Workspace/i })).not.toBeInTheDocument();
  });
});
