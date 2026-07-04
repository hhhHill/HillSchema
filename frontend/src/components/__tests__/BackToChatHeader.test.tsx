import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import BackToChatHeader from '../BackToChatHeader';

describe('BackToChatHeader', () => {
  it('uses the approved Chinese back label for the problem list', () => {
    render(
      <MemoryRouter>
        <BackToChatHeader title="示例页面" subtitle="示例副标题" />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /返回问题列表/i })).toBeInTheDocument();
  });
});
