import axios from 'axios';

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

const PUBLIC_ENDPOINTS = [
  '/identities/auth/login',
  '/identities/auth/introspect',
  '/identities/auth/outbound/google',
  '/identities/users/forgot-password',
  '/identities/users/reset-password',
  '/identities/users/registration',
];

axiosInstance.interceptors.request.use(
  (config) => {
    // Normalize URL to start with /
    const url = config.url?.startsWith('/') ? config.url : `/${config.url}`;
    
    const isPublic = PUBLIC_ENDPOINTS.some(endpoint => url.startsWith(endpoint)) || 
                     url.startsWith('/files/media/download/');

    if (isPublic) {
      // Ensure NO Authorization header is sent for public endpoints
      delete config.headers.Authorization;
    } else {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default axiosInstance;
