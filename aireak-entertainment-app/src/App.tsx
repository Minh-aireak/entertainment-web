import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Provider, useSelector, useDispatch } from 'react-redux';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Toaster } from 'react-hot-toast';
import { store, type RootState, logout, setUser } from './store';
import { getTheme } from './theme';
import { profileService } from './api/profileService';
import { identityService } from './api/identityService';
import type { Role } from './models';
import { useEffect } from 'react';

import MainLayout from './components/Layout/MainLayout';
import ProtectedRoute from './components/Auth/ProtectedRoute';
import { WebSocketProvider } from './contexts/WebSocketContext';
import { ConfirmDialogProvider } from './contexts/ConfirmDialogContext';

import SocialHome from './pages/SocialHome';
import TravelHome from './pages/TravelHome';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Authenticate from './pages/Authenticate';
import ItineraryPage from './pages/Itinerary';
import ChatPage from './pages/Chat';

import ProfilePage from './pages/Profile';
import FriendsPage from './pages/Friends';
import NotificationsPage from './pages/Notifications';
import AdminPage from './pages/Admin';

import FilmHome from './pages/Film/FilmHome';
import FilmTrending from './pages/Film/FilmTrending';
import FilmLibrary from './pages/Film/FilmLibrary';
import FilmDetail from './pages/Film/FilmDetail';
import EpisodeUpload from './pages/Film/EpisodeUpload';

function AppContent() {
  const dispatch = useDispatch();
  const themeMode = useSelector((state: RootState) => state.ui.themeMode);
  const { user } = useSelector((state: RootState) => state.auth);
  const theme = getTheme(themeMode);

  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      try {
        // This request also drives the 401 -> refresh-token -> retry flow.
        const response = await profileService.getMyProfile();
        if (!cancelled && response.code === 1000 && response.result) {
          const profile = response.result;
          let roles: Role[] = [{ name: 'USER', description: 'Default user role' }];
          try {
            const myInfoResponse = await identityService.getMyInfo();
            if (myInfoResponse.code === 1000 && myInfoResponse.result) {
              roles = myInfoResponse.result.roles;
            }
          } catch (roleError) {
            console.debug('Could not load roles for restored session:', roleError);
          }
          if (cancelled) return;
          dispatch(setUser({
            id: profile.userId,
            username: profile.username,
            email: profile.email,
            roles,
          }));
          return;
        }
      } catch (error) {
        console.debug('No restorable authenticated session:', error);
      }

      // Do not let an older bootstrap request overwrite a login that just succeeded.
      if (!cancelled && !store.getState().auth.isAuthenticated) {
        dispatch(logout());
      }
    };

    if (!user) {
      restoreSession();
    }

    return () => {
      cancelled = true;
    };
  }, [dispatch, user]);

  return (
    <ThemeProvider theme={theme}>
        <CssBaseline />
        <Toaster 
          position="top-right"
          toastOptions={{
            duration: 4000,
            style: {
              background: theme.palette.background.paper,
              color: theme.palette.text.primary,
              padding: '16px 24px',
              borderRadius: '12px',
              boxShadow: '0 10px 40px rgba(0, 0, 0, 0.2)',
              border: `1px solid ${theme.palette.divider}`,
              fontSize: '15px',
              fontWeight: 500,
            },
            success: {
              duration: 3000,
              iconTheme: {
                primary: theme.palette.success.main,
                secondary: theme.palette.success.contrastText,
              },
            },
            error: {
              duration: 5000,
              iconTheme: {
                primary: theme.palette.error.main,
                secondary: theme.palette.error.contrastText,
              },
            },
            loading: {
              style: {
                background: theme.palette.background.paper,
                color: theme.palette.text.primary,
                border: `1px solid ${theme.palette.divider}`,
              },
            },
          }}
        />
        <Router>
          <WebSocketProvider>
          <ConfirmDialogProvider>
            <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/authenticate" element={<Authenticate />} />

            {/* Legacy redirects */}
            <Route path="/" element={<Navigate to="/social" replace />} />
            <Route path="/chat" element={<Navigate to="/social/chat" replace />} />
            <Route path="/friends" element={<Navigate to="/social/friends" replace />} />
            <Route path="/profile" element={<Navigate to="/social/profile" replace />} />
            <Route path="/itinerary" element={<Navigate to="/travel/itinerary" replace />} />

            {/* Social module */}
            <Route
              path="/social"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <SocialHome />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/social/chat"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ChatPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/social/friends"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <FriendsPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/social/profile"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ProfilePage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/social/notifications"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <NotificationsPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

            {/* Travel module */}
            <Route
              path="/travel"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <TravelHome />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/travel/itinerary"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <ItineraryPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

            {/* Film module */}
            <Route
              path="/film"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <FilmHome />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/film/trending"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <FilmTrending />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/film/library"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <FilmLibrary />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/film/:id"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <FilmDetail />
                  </MainLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/film/:filmId/upload-episode"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <MainLayout>
                    <EpisodeUpload />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

            <Route
              path="/admin"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <MainLayout>
                    <AdminPage />
                  </MainLayout>
                </ProtectedRoute>
              }
            />

              <Route path="*" element={<Navigate to="/social" replace />} />
            </Routes>
          </ConfirmDialogProvider>
          </WebSocketProvider>
        </Router>
      </ThemeProvider>
  );
}

function App() {
  return (
    <Provider store={store}>
      <AppContent />
    </Provider>
  );
}

export default App;
