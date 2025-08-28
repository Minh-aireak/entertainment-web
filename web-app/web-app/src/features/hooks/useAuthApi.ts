import { httpClient } from "../../configurations/httpClient";
import { API_ENDPOINTS } from "../../configurations/configuration";
import AuthClientStore from "../client-store/AuthClientStore";
import type { LoginResponse } from "../../InterfaceDataType/DataTypeResponse";

let debouncedPromise: Promise<unknown> | null = null;
let debouncedResolve: (...args: unknown[]) => void;
let debouncedReject: (...args: unknown[]) => void;
let timeout: number;

export const logIn = async (
  username: string,
  password: string
): Promise<LoginResponse> => {
  const response = await httpClient.post<LoginResponse>(API_ENDPOINTS.LOGIN, {
    username,
    password,
  });

  AuthClientStore.setAccessToken(response.data.result?.token);

  return response.data;
};

export const logOut = (): void => {
  AuthClientStore.removeAccessToken();
};

export const isAuthenticated = (): boolean => {
  return AuthClientStore.getAccessToken() !== null;
};

export const refreshToken = (token: string): Promise<LoginResponse> => {
  clearTimeout(timeout);
  if (!debouncedPromise) {
    debouncedPromise = new Promise((resolve, reject) => {
      debouncedResolve = resolve;
      debouncedReject = reject;
    });
  }

  timeout = setTimeout(() => {
    const executeLogic = async () => {
      const response = await httpClient.post<LoginResponse>(
        API_ENDPOINTS.REFRESH_TOKEN,
        {
          token,
        }
      );
      AuthClientStore.setAccessToken(response.data.result?.token);
    };

    executeLogic().then(debouncedResolve).catch(debouncedReject);
  }, 200);

  return debouncedPromise as Promise<LoginResponse>;
};