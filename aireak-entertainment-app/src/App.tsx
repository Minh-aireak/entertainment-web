import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Toaster } from 'react-hot-toast';
import { store } from './store';
import theme from './theme';

import MainLayout from './components/Layout/MainLayout';
import ProtectedRoute from './components/Auth/ProtectedRoute';

import SocialHome from './pages/SocialHome';
import TravelHome from './pages/TravelHome';
import Login from './pages/Login';
import Register from './pages/Register';
import ItineraryPage from './pages/Itinerary';
import ChatPage from './pages/Chat';
import WeatherPage from './pages/Weather';
import ProfilePage from './pages/Profile';
import FriendsPage from './pages/Friends';
import AdminPage from './pages/Admin';

function App() {
  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Toaster position="top-right" />
        <Router>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />

            {/* Legacy redirects */}
            <Route path="/" element={<Navigate to="/social" replace />} />
            <Route path="/chat" element={<Navigate to="/social/chat" replace />} />
            <Route path="/friends" element={<Navigate to="/social/friends" replace />} />
            <Route path="/profile" element={<Navigate to="/social/profile" replace />} />
            <Route path="/itinerary" element={<Navigate to="/travel/itinerary" replace />} />
            <Route path="/weather" element={<Navigate to="/travel/weather" replace />} />

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
            <Route
              path="/travel/weather"
              element={
                <ProtectedRoute>
                  <MainLayout>
                    <WeatherPage />
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
        </Router>
      </ThemeProvider>
    </Provider>
  );
}

export default App;
