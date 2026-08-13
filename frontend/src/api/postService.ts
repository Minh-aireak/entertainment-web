import axiosInstance from './axiosInstance';
import type {
  ApiResponse,
  PostResponse,
  PostRequest,
  PostUpdateRequest,
  PageResponse,
  LikeResponse
} from '../models';

export const postService = {
  getPosts: (page: number, size: number) =>
    axiosInstance.get<ApiResponse<PageResponse<PostResponse>>>(`/posts?page=${page}&size=${size}`),

  getPostById: (id: string) =>
    axiosInstance.get<ApiResponse<PostResponse>>(`/posts/${id}`),

  createPost: (data: PostRequest) =>
    axiosInstance.post<ApiResponse<PostResponse>>('/posts', data),

  updatePost: (id: string, data: PostUpdateRequest) =>
    axiosInstance.put<ApiResponse<PostResponse>>(`/posts/${id}`, data),

  deletePost: (id: string) =>
    axiosInstance.delete<ApiResponse<void>>(`/posts/${id}`),

  toggleLike: (id: string) =>
    axiosInstance.post<ApiResponse<LikeResponse>>(`/posts/${id}/like`),

  getRandomPosts: (limit: number, excludeIds: string[] = []) =>
    axiosInstance.get<ApiResponse<PostResponse[]>>('/posts/random', {
      params: {
        limit,
        excludeIds: excludeIds.length ? excludeIds.join(',') : undefined,
      },
    }),
};
