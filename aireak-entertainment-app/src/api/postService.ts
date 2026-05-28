import axiosInstance from './axiosInstance';
import type { 
  ApiResponse, 
  ScheduleResponse, 
  ScheduleRequest, 
  ScheduleUpdateRequest, 
  PageResponse,
  StatusResponse
} from '../models';

export const postService = {
  getPosts: (page: number, size: number, type: string) => 
    axiosInstance.get<ApiResponse<PageResponse<ScheduleResponse>>>(`/post?page=${page}&size=${size}&type=${type}`),
  
  getPostById: (id: string, type: string) =>
    axiosInstance.get<ApiResponse<ScheduleResponse>>(`/post/${id}?type=${type}`),

  createPost: (data: ScheduleRequest) => 
    axiosInstance.post<ApiResponse<ScheduleResponse>>('/post', data),
  
  updatePost: (id: string, type: string, data: ScheduleUpdateRequest) =>
    axiosInstance.put<ApiResponse<ScheduleResponse>>(`/post/${id}?type=${type}`, data),

  deletePost: (id: string) =>
    axiosInstance.delete<ApiResponse<void>>(`/post/${id}`),

  getStatus: () =>
    axiosInstance.get<ApiResponse<StatusResponse>>('/post/status'),
};
