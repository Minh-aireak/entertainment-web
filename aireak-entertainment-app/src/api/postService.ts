import axiosInstance from './axiosInstance';
import type {
  ApiResponse,
  ScheduleResponse,
  ScheduleRequest,
  ScheduleUpdateRequest,
  PageResponse,
  StatusResponse,
  LikeResponse
} from '../models';

export const postService = {
  getPosts: (page: number, size: number, type: string) =>
    axiosInstance.get<ApiResponse<PageResponse<ScheduleResponse>>>(`/posts?page=${page}&size=${size}&type=${type}`),

  getPostById: (id: string, type: string) =>
    axiosInstance.get<ApiResponse<ScheduleResponse>>(`/posts/${id}/${type}`),

  createPost: (data: ScheduleRequest) =>
    axiosInstance.post<ApiResponse<ScheduleResponse>>('/posts', data),

  updatePost: (id: string, type: string, data: ScheduleUpdateRequest) =>
    axiosInstance.put<ApiResponse<ScheduleResponse>>(`/posts/${id}/${type}`, data),

  deletePost: (id: string, type: string) =>
    axiosInstance.delete<ApiResponse<void>>(`/posts/${id}/${type}`),

  toggleLike: (id: string, type: string) =>
    axiosInstance.post<ApiResponse<LikeResponse>>(`/posts/${id}/${type}/like`),

  getStatus: () =>
    axiosInstance.get<ApiResponse<StatusResponse>>('/posts/status'),
};
