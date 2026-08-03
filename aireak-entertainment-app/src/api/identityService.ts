import axiosInstance from './axiosInstance';
import type { 
  ApiResponse, 
  IntrospectResponse,
  UserResponse, 
  AuthenticationRequest, 
  UserCreationRequest,
  RoleCreationRequest,
  RoleUpdateRequest,
  RoleResponse,
  PermissionCreationRequest,
  PermissionUpdateRequest,
  PermissionResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  PageResponse,
} from '../models';

export const identityService = {
  // Authentication
  login: async (data: AuthenticationRequest) => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/auth/login', data);
    return response.data;
  },

  introspect: async (token: string) => {
    const response = await axiosInstance.post<ApiResponse<IntrospectResponse>>(
      '/identities/auth/introspect',
      null,
      { params: { token } },
    );
    return response.data;
  },

  logout: async () => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/auth/logout');
    return response.data;
  },
      
  refresh: async () => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/auth/refresh-token');
    return response.data;
  },

  outboundAuthenticate: async (code: string) => {
    const response = await axiosInstance.post<ApiResponse<void>>(`/identities/auth/outbound/google?code=${code}`);
    return response.data;
  },

  // Permissions
  createPermission: async (data: PermissionCreationRequest) => {
    const response = await axiosInstance.post<ApiResponse<PermissionResponse>>('/identities/permissions', data);
    return response.data;
  },

  getPermissions: async () => {
    const response = await axiosInstance.get<ApiResponse<PermissionResponse[]>>('/identities/permissions');
    return response.data;
  },

  updatePermission: async (data: PermissionUpdateRequest) => {
    const response = await axiosInstance.put<ApiResponse<PermissionResponse>>(`/identities/permissions/`, data);
    return response.data;
  },  

  deletePermission: async (permissionName: string) => {
    const response = await axiosInstance.delete<ApiResponse<void>>(`/identities/permissions/${permissionName}`);
    return response.data;
  },

  // Roles
  createRole: async (data: RoleCreationRequest) => {
    const response = await axiosInstance.post<ApiResponse<RoleResponse>>('/identities/roles', data);
    return response.data;
  },

  getRoles: async () => {
    const response = await axiosInstance.get<ApiResponse<RoleResponse[]>>('/identities/roles');
    return response.data;
  },

  updateRole: async (data: RoleUpdateRequest) => {
    const response = await axiosInstance.put<ApiResponse<RoleResponse>>(`/identities/roles/`, data);
    return response.data;
  },

  deleteRole: async (roleName: string) => {
    const response = await axiosInstance.delete<ApiResponse<void>>(`/identities/roles/${roleName}`);
    return response.data;
  },

  // Users
  registration: async (data: UserCreationRequest) => {
    const response = await axiosInstance.post<ApiResponse<UserResponse>>('/identities/users/registration', data);
    return response.data;
  },

  changePassword: async (data: ChangePasswordRequest) => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/users/password', data);
    return response.data;
  },

  getUsers: async (page: number = 1, size: number = 10) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<UserResponse>>>('/identities/users', {
      params: {
        page,
        size,
      }
    });
    return response.data;
  },

  toggleAccount: async (userId: string) => {
    const response = await axiosInstance.post<ApiResponse<void>>(`/identities/users/${userId}/toggle-account`);
    return response.data;
  },

  forgotPassword: async (data: ForgotPasswordRequest) => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/users/forgot-password', data);
    return response.data;
  },

  resetPassword: async (data: ResetPasswordRequest) => {
    const response = await axiosInstance.post<ApiResponse<void>>('/identities/users/reset-password', data);
    return response.data;
  },
};
