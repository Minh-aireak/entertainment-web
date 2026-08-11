import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProtectedRoute from './ProtectedRoute';

const mocks = vi.hoisted(() => ({
  useSelector: vi.fn(),
}));

vi.mock('react-redux', () => ({
  useSelector: mocks.useSelector,
}));

interface AuthState {
  isAuthenticated: boolean;
  loading: boolean;
  user: null | { roles: { name: string }[] };
}

function renderProtectedRoute(auth: AuthState, roles?: string[]) {
  mocks.useSelector.mockReturnValue(auth);

  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route
          path="/admin"
          element={
            <ProtectedRoute roles={roles}>
              <div>Admin content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/social" element={<div>Social page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    mocks.useSelector.mockReset();
  });

  it('shows a loading state while authentication is being restored', () => {
    renderProtectedRoute({ isAuthenticated: false, loading: true, user: null });

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('redirects unauthenticated users to login', () => {
    renderProtectedRoute({ isAuthenticated: false, loading: false, user: null });

    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });

  it('redirects authenticated users without an allowed role', () => {
    renderProtectedRoute(
      {
        isAuthenticated: true,
        loading: false,
        user: { roles: [{ name: 'USER' }] },
      },
      ['ADMIN'],
    );

    expect(screen.getByText('Social page')).toBeInTheDocument();
  });

  it('renders the protected content for an allowed role', () => {
    renderProtectedRoute(
      {
        isAuthenticated: true,
        loading: false,
        user: { roles: [{ name: 'ADMIN' }] },
      },
      ['ADMIN'],
    );

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('renders authenticated routes that do not require a role', () => {
    renderProtectedRoute({
      isAuthenticated: true,
      loading: false,
      user: { roles: [{ name: 'USER' }] },
    });

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });
});
