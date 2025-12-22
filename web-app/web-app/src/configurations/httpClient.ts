import axios from "axios";
import AuthClientStore from "../features/client-store/AuthClientStore";
import { refreshToken } from "../services/Authenticate";
import { CONFIG, API_ENDPOINTS } from "./configuration";

let isRefreshing = false;
let queue: { resolve: (token: string) => void; reject: (err: any) => void }[] =
  [];

const flushQueue = (token: string | null, err?: any) => {
  queue.forEach(({ resolve, reject }) =>
    token ? resolve(token) : reject(err)
  );
  queue = [];
};

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
  (resp) => resp,
  async (error) => {
    const originalRequest = error?.config;
    const status = error?.response?.status;

    if (!originalRequest) return Promise.reject(error);

    if (
      originalRequest.url &&
      originalRequest.url.includes(API_ENDPOINTS.REFRESH_TOKEN)
    ) {
      AuthClientStore.removeAccessToken();
      if (typeof AuthClientStore.removeAccessToken === "function") {
        AuthClientStore.removeAccessToken();
      }
      window.location.href = "/login";
      return Promise.reject(error);
    }

    if (status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      const oldToken = AuthClientStore.getAccessToken();

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          queue.push({
            resolve: (token: string) => {
              originalRequest.headers = originalRequest.headers || {};
              originalRequest.headers["Authorization"] = `Bearer ${token}`;
              resolve(httpClient(originalRequest));
            },
            reject,
          });
        });
      }

      isRefreshing = true;
      try {
        const newToken = await refreshToken(oldToken!);

        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers["Authorization"] = `Bearer ${newToken}`;

        flushQueue(newToken.result.token);

        return httpClient(originalRequest);
      } catch (refreshErr) {
        flushQueue(null, refreshErr);
        AuthClientStore.removeAccessToken();
        if (typeof AuthClientStore.removeAccessToken === "function") {
          AuthClientStore.removeAccessToken();
        }
        window.location.href = "/login";
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
