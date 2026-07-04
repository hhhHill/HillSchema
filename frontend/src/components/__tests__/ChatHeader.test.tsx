import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import ChatHeader from '../ChatHeader';

describe('ChatHeader', () => {
  it('uses the approved Chinese label for the problem home entry', () => {
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

    expect(screen.getByRole('button', { name: /问题/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Skills/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Workspace/i })).not.toBeInTheDocument();
  });
});
