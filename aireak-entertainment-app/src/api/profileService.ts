import axiosInstance from './axiosInstance';
import type { UserFullSummaryResponse, ApiResponse, UserProfileResponse, UserProfileUpdateRequest, PageResponse } from '../models';

export const profileService = {
  updateProfile: async (data: UserProfileUpdateRequest) => {
    const response = await axiosInstance.put<ApiResponse<UserProfileResponse>>('/profiles/my-profile', data);
    return response.data;
  },

  getMyProfile: async () => {
    const response = await axiosInstance.get<ApiResponse<UserProfileResponse>>('/profiles/my-profile');
    return response.data;
  },

  getAllProfiles: async (page: number, pageSize: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<UserProfileResponse>>>('/profiles', {
      params: {
        page,
        pageSize,
      },
    });
    return response.data;
  },

  searchProfile: async (displayName: string, page: number, size: number) => {
    const response = await axiosInstance.post<ApiResponse<PageResponse<UserProfileResponse>>>('/profiles/search/${displayName}', {
      params: {
        displayName,
        page,
        size,
      },
    });
    return response.data;
  },
  
  getProfileByUserId: async (userId: string) => {
    const response = await axiosInstance.get<ApiResponse<UserProfileResponse>>(`/profiles/${userId}`);
    return response.data;
  },

  getUserSummary: async () => {
    const response = await axiosInstance.get<ApiResponse<UserFullSummaryResponse>>('/profiles/my-summary');
    return response.data;
  },
};
