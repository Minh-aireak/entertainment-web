import axiosInstance from './axiosInstance';
import type { ApiResponse, ChatMessageCreateRequest, ChatMessageResponse, ConversationResponse, PageResponse, ChatMessageDeleteRequest, ChatMessageUpdateRequest, UnreadCountResponse} from '../models';

export const chatService = {
  createChatMessage: async (data: ChatMessageCreateRequest) => { 
    const response = await axiosInstance.post<ApiResponse<ChatMessageResponse>>('/chats/messages', data);
    return response.data;
  },
  
  searchMessages: async (conversationId: string, query: string, page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<ChatMessageResponse>>>(`/chats/messages/${conversationId}/search`, {
      params: {
        query,
        page,
        size,
      },
    });
    return response.data;
  },
  
  getMyChatMessages: async (conversationId: string, page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<ChatMessageResponse>>>('/chats/messages/my-messages', {
      params: {
        conversationId,
        page,
        size,
      },
    });
    return response.data;
  },

  deleteChatMessage: async (data: ChatMessageDeleteRequest) =>
    await axiosInstance.delete<ApiResponse<void>>("/chats/messages",
      {
        data,
      }
    ),
  
  updateChatMessage: async (data: ChatMessageUpdateRequest) => {
    const response = await axiosInstance.put<ApiResponse<ChatMessageResponse>>(`/chats/messages`, data);
    return response.data;
  },

  seenAt: async (conversationId: string) => 
    await axiosInstance.put<ApiResponse<void>>(`/chats/messages/mark-as-seen/${conversationId}`),
  
  getUnreadCount: async () => {
    const response = await axiosInstance.get<ApiResponse<UnreadCountResponse>>(`/chats/messages/unread-count`); 
    return response.data;
  },

  createConversation: async (ids: string[]) => {
    const response = await axiosInstance.post<ApiResponse<ConversationResponse>>('/chats/conversations', ids);
    return response.data;
  },
  
  getMyConversations: async (page: number, size: number) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<ConversationResponse>>>('/chats/conversations/my-conversations', {
      params: {
        page,
        size,
      },
    });
    return response.data;
  },

  searchConversations: async (query: string, page: number = 1, size: number = 10) => {
    const response = await axiosInstance.get<ApiResponse<PageResponse<ConversationResponse>>>('/chats/conversations/search', {
      params: {
        query,
        page,
        size,
      },
    });
    return response.data;
  },

  updateConversation: async (conversationId: string, data: { conversationName?: string; conversationAvatar?: string }) => {
    const response = await axiosInstance.put<ApiResponse<ConversationResponse>>(`/chats/conversations/${conversationId}`, data);
    return response.data;
  },
};
