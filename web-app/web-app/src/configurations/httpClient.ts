import axios from "axios";
import AuthClientStore from "../features/client-store/AuthClientStore";
import { refreshToken } from "../features/hooks/useAuthApi";
import { CONFIG } from "./configuration";

export const httpClient = axios.create({
  baseURL: CONFIG.API_GATE_WAY,
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

httpClient.interceptors.request.use((config) => {
  const token = AuthClientStore.getAccessToken();

  config.headers = config.headers || {};
  if (token) {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const tokenForRefresh = AuthClientStore.getAccessToken();
        const response = await refreshToken(tokenForRefresh!);
        const accessToken = response.result.token;

        httpClient.defaults.headers.common[
          "Authorization"
        ] = `Bearer ${accessToken}`;
        AuthClientStore.setAccessToken(accessToken);
        return httpClient(originalRequest);
      } catch (refreshError) {
        AuthClientStore.removeAccessToken();
        window.location.href = "/login";
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);