import axiosInstance from './axiosInstance';
import type { UserFullSummaryResponse, ApiResponse, UserProfileResponse, UserProfileUpdateRequest, PageResponse } from '../models';

export const profileService = {
  updateProfile: async (data: UserProfileUpdateRequest) => {
    const response = await axiosInstance.put<ApiResponse<UserProfileResponse>>('/profiles/my-profile', data);
    return response.data;
  },

  updateAvatar: async (avatarFileId: string) => {
    const response = await axiosInstance.put<ApiResponse<UserProfileResponse>>('/profiles/my-profile/avatar', {
      avatarFileId,
    });
    return response.data;
  },

  getMyProfile: async () => {
    const response = await axiosInstance.get<ApiResponse<UserProfileResponse>>('/profiles/my-profile');
    return response.data;
  },

  getAllProfiles: async (page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<UserProfileResponse>>>('/profiles/suggestions', {
      params: {
        page: page - 1,
        size,
      },
    });
    return response.data;
  },

  searchProfile: async (displayName: string, page: number, size: number) => {
    const response = await axiosInstance.post<ApiResponse<PageResponse<UserProfileResponse>>>(`/profiles/search/${displayName}`, null, {
      params: {
        displayName,
        page: page - 1,
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
    const response = await axiosInstance.get<ApiResponse<UserFullSummaryResponse>>('/profiles/aggregation/my-summary');
    return response.data;
  },
};
