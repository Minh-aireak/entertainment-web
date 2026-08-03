import axiosInstance from './axiosInstance';
import type { ApiResponse, PageResponse, UserRelationshipResponse, FriendRequestStatus, RelationshipStatus, FriendRequestResponse } from '../models';

export const friendService = {
  sendFriendRequest: async (toUserId: string) => {
    const response = await axiosInstance.post<ApiResponse<void>>(`/friends/requests/${toUserId}`);
    return response.data;
  },

  getMyFriends: async (page: number = 1, size: number = 10) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<UserRelationshipResponse>>>('/friends/my-friends', {
      params: {
        page,
        size,
      },
    });
    return response.data;
  },
  
  searchFriends: async (displayName: string, page: number = 1, size: number = 10) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<UserRelationshipResponse>>>('/friends/search', {
      params: {
        displayName,
        page,
        size,
      },
    });
    return response.data;
  },

  friendRequestStatus: async (senderId: string, status: FriendRequestStatus) => {
    const response = await axiosInstance.put<ApiResponse<void>>(`/friends/requests/${senderId}`, null, {
      params: { status },
    });
    return response.data;
  },

  updateRelationshipStatus: async (toUserId: string, status: RelationshipStatus) => {
    const response = await axiosInstance.put<ApiResponse<void>>(`/friends/relationship/${toUserId}`, null, {
      params: { status },
    });
    return response.data;
  },

  getMyFriendRequests: async (page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<FriendRequestResponse>>>('/friends/requests', {
      params: {
        page,
        size,
      },
    });
    return response.data;
  },  
};
