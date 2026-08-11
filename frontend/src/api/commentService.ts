import axiosInstance from './axiosInstance';
import type { 
  ApiResponse, 
  PageResponse 
} from '../models';

export type CommentType = 'TEXT' | 'ICON';
export type CommentStatus = 'SENT' | 'EDITED' | 'DELETED';
export type CommentReactionType = 'LIKE' | 'LOVE';

export interface CommentResponse {
  id: string;
  sourceId: string;
  userId: string;
  content: string;
  parentId: string;
  topParentId: string;
  likeCount: number;
  loveCount: number;
  replyCount: number;
  type: CommentType;
  status: CommentStatus;
  durationCreatedDate: string;
  avatar: string;
  displayName: string;
  /** Per-viewer field: only ever present on REST responses (GET/POST/PUT/reactions), never on
   *  realtime broadcasts - see useCommentSocket.ts, which preserves the locally-known value
   *  instead of overwriting it from a "comment:created"/"comment:updated" event. */
  myReaction?: CommentReactionType | null;
}

export interface CommentReactionResponse {
  commentId: string;
  likeCount: number;
  loveCount: number;
  myReaction: CommentReactionType | null;
}

export interface CommentReactionChangedEvent {
  commentId: string;
  sourceId: string;
  parentId?: string;
  actorUserId: string;
  likeCount: number;
  loveCount: number;
  myReaction: CommentReactionType | null;
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

  /** Total comment count for a post INCLUDING replies - unlike getComments' totalElement,
   *  which only counts top-level comments (used for paginating the root-comment list). */
  getCommentCount: async (sourceId: string) => {
    const response = await axiosInstance.get<ApiResponse<number>>(
      `/comments/count?sourceId=${sourceId}`
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

  getReplies: async (commentId: string, page: number = 1, size: number = 5) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<CommentResponse>>>(
      `/comments/${commentId}/replies?page=${page}&size=${size}`
    );
    return response.data;
  },

  react: async (commentId: string, type: CommentReactionType) => {
    const response = await axiosInstance.post<ApiResponse<CommentReactionResponse>>(
      `/comments/${commentId}/reactions`,
      { type }
    );
    return response.data;
  },
};
