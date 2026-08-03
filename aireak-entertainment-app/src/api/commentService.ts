import axiosInstance from './axiosInstance';
import type { 
  ApiResponse, 
  PageResponse 
} from '../models';

export type CommentType = 'TEXT' | 'ICON';
export type CommentStatus = 'SENT' | 'EDITED' | 'DELETED';

export interface CommentResponse {
  id: string;
  sourceId: string;
  userId: string;
  content: string;
  parentId: string;
  topParentId: string;
  likeCount: number;
  replyCount: number;
  type: CommentType;
  status: CommentStatus;
  durationCreatedDate: string;
  avatar: string;
  displayName: string;
}

export interface CreateCommentRequest {
  sourceId: string;
  content: string;
  type: CommentType;
  parentId?: string;
  topParentId?: string;
  listIdsJoin?: string[];
}

export interface UpdateCommentRequest {
  content: string;
}

export const commentService = {
  getComments: async (sourceId: string, page: number = 1, size: number = 10) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<CommentResponse>>>(
      `/comments?sourceId=${sourceId}&page=${page}&size=${size}`
    );
    return response.data;
  },

  createComment: async (data: CreateCommentRequest) => {
    const response = await axiosInstance.post<ApiResponse<CommentResponse>>('/comments', data);
    return response.data;
  },

  updateComment: async (commentId: string, data: UpdateCommentRequest) => {
    const response = await axiosInstance.put<ApiResponse<CommentResponse>>(`/comments/${commentId}`, data);
    return response.data;
  },

  deleteComment: async (commentId: string) => {
    const response = await axiosInstance.delete<ApiResponse<void>>(`/comments/${commentId}`);
    return response.data;
  },
};
