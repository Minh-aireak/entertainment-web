import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import { store, logout } from '../store';

// 1. Constants & Types
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888/api/v1';

const PUBLIC_ENDPOINTS = [
  '/identities/auth/login',
  '/identities/auth/introspect',
  '/identities/auth/outbound/google',
  '/identities/auth/refresh-token',
  '/identities/auth/logout',
  '/identities/users/forgot-password',
  '/identities/users/reset-password',
  '/identities/users/registration',
];

interface FailedRequest {
  resolve: () => void;
  reject: (error: any) => void;
}

const LEGACY_TOKEN_KEYS = ['token', 'accessToken', 'refreshToken', 'access_token', 'refresh_token'];

export const clearClientAuthState = () => {
  LEGACY_TOKEN_KEYS.forEach((key) => localStorage.removeItem(key));
  LEGACY_TOKEN_KEYS.forEach((key) => sessionStorage.removeItem(key));
};

// 2. State Management for Refresh Logic
let isRefreshing = false;
let failedQueue: FailedRequest[] = [];

/**
 * Process the queue of requests that were waiting for a new token.
 */
const processQueue = (error: any) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve();
    }
  });
  failedQueue = [];
};

// 3. Create Axios Instance
const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000, // 15s timeout for better UX
  withCredentials: true, // Important: This allows sending and receiving cookies!
});

// Helper to get CSRF token from cookies
const getCsrfToken = () => {
  const name = 'XSRF-TOKEN';
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop()?.split(';').shift();
  return null;
};

// 4. Request Interceptor
axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Normalize URL
    const url = config.url?.startsWith('/') ? config.url : `/${config.url}`;
    
    // Check if endpoint is public
    const isPublic = PUBLIC_ENDPOINTS.some(endpoint => url.startsWith(endpoint)) || 
                     url.startsWith('/files/media/download/');

    if (isPublic) {
      delete config.headers.Authorization;
    } else {
      const csrfToken = getCsrfToken();
      if (csrfToken) {
        config.headers['X-XSRF-TOKEN'] = csrfToken;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 5. Response Interceptor (The core of Auto-Refresh)
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // 5.1. Handle 401 Unauthorized errors
    if (error.response?.status === 401 && !originalRequest._retry) {
      
      // If we are already refreshing, queue this request
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: () => {
              resolve(axiosInstance(originalRequest));
            },
            reject: (err: any) => reject(err),
          });
        });
      }

      // Start refreshing process
      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Use a clean axios instance to avoid infinite loops and redundant interceptors
        const refreshResponse = await axios.post(`${API_BASE_URL}/identities/auth/refresh-token`, null, {
          withCredentials: true,
        });

        if (refreshResponse.data.code === 1000) {
          // Process queue with new token
          processQueue(null);
          
          // Retry original request
          return axiosInstance(originalRequest);
        } else {
          throw new Error('Refresh failed');
        }
      } catch (refreshError) {
        processQueue(refreshError);
        handleLogout();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

/**
 * Clean fallback for logout.
 */
const handleLogout = () => {
  clearClientAuthState();
  store.dispatch(logout());
  
  // Prevent infinite loops if already on login page
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login';
  }
};

export default axiosInstance;
