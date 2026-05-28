import axiosInstance from './axiosInstance';
import type { ApiResponse, NotificationResponse, PageResponse } from '../models';

export const notificationService = {
  getMyNotifications: async (pageNum: number, pageSize: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<NotificationResponse>>>('/notifications/my-notifications',
      {
        params: {
          pageNum,
          pageSize,
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
    await axiosInstance.post<ApiResponse<void>>(`/notifications/mark-all-as-read`);
  },
};
