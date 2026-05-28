import axiosInstance from './axiosInstance';
import type { ApiResponse, PageResponse, UserRelationshipResponse, FriendRequestStatus, RelationshipStatus, FriendRequestResponse } from '../models';

export const friendService = {
  sendFriendRequest: async (toUserId: string) => {
    return await axiosInstance.post<ApiResponse<void>>(`/friends/requests/${toUserId}`)
  },

  getMyFriends: async (page: number = 1, pageSize: number = 10) => {
    return await axiosInstance.get<ApiResponse<PageResponse<UserRelationshipResponse>>>('/my-friends', {
      params: {
        page,
        pageSize,
      },
    });
  },
  
  searchFriends: async (displayName: string, page: number = 1, pageSize: number = 10) => {
    return await axiosInstance.get<ApiResponse<PageResponse<UserRelationshipResponse>>>('/friends/search', {
      params: {
        displayName,
        page,
        pageSize,
      },
    });
  },

  friendRequestStatus: async (senderId: string, status: FriendRequestStatus) => {
    return await axiosInstance.put<ApiResponse<void>>(`/friends/requests/${senderId}`, {
      params: {
        status,
      },
    })
  },

  updateRelationshipStatus: async (toUserId: string, status: RelationshipStatus) => {
    return await axiosInstance.put<ApiResponse<void>>(`/friends/relationship/${toUserId}`, {
      status,
    })
  },

  getMyFriendRequests: async (page: number, size: number) => {
    return await axiosInstance.get<ApiResponse<PageResponse<FriendRequestResponse>>>('/requests', {
      params: {
        page,
        size,
      },
    });
  },  
};
