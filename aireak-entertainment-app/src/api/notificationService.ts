import axiosInstance from './axiosInstance';
import type { ApiResponse, NotificationResponse, PageResponse } from '../models';

export const notificationService = {
  getMyNotifications: async (page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<NotificationResponse>>>('/notifications/my-notifications',
      {
        params: {
          page,
          size,
        }
      }
    );
    return response.data;
  },

  getUnreadCount: async () => {
    const response = await axiosInstance.get<ApiResponse<number>>('/notifications/unread-count');
    return response.data;
  },
  
  markAllAsRead: async () => {
    const response = await axiosInstance.put<ApiResponse<void>>('/notifications/mark-all-as-read');
    return response.data;
  },
};
