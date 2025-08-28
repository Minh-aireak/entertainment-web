import { httpClient } from "../../configurations/httpClient";
import { API_ENDPOINTS } from "../../configurations/configuration";
import AuthClientStore from "../client-store/AuthClientStore";
import type { LoginResponse } from "../../InterfaceDataType/DataTypeResponse";

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

export const refreshToken = async (token: string): Promise<LoginResponse> => {
  const response = await httpClient.post<LoginResponse>(
    API_ENDPOINTS.REFRESH_TOKEN,
    {
      token,
    }
  );
  AuthClientStore.setAccessToken(response.data.result?.token);
  return response.data;
};
