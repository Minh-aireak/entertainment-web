import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  ChatMessageCreateRequest,
  ChatMessageDeleteRequest,
  ChatMessageResponse,
  ChatMessageUpdateRequest,
  ConversationResponse,
} from "../InterfaceDataType/DataType";

export const createConversation = async (
  data: string[]
): Promise<ConversationResponse> => {
  return (await httpClient.post(API_ENDPOINTS.CREATE_CONVERSATION, data)).data
    .result as ConversationResponse;
};

export const getMyConversations = async (): Promise<ConversationResponse[]> => {
  return (await httpClient.get(API_ENDPOINTS.GET_MY_CONVERSATIONS)).data
    .result as ConversationResponse[];
};

export const createChatMessage = async (
  data: ChatMessageCreateRequest
): Promise<ChatMessageResponse> => {
  return (await httpClient.post(API_ENDPOINTS.CREATE_MESSAGE, data)).data
    .result as ChatMessageResponse;
};

export const getMyChatMessages = async (
  conversationId: string,
  page: number,
  size: number
): Promise<ChatMessageResponse[]> => {
  return (
    await httpClient.get(API_ENDPOINTS.GET_MY_MESSAGES, {
      params: { conversationId: conversationId, page: page, size: size },
    })
  ).data.result.data as ChatMessageResponse[];
};

export const deleteChatMessage = async (
  data: ChatMessageDeleteRequest
): Promise<ChatMessageResponse> => {
  return (await httpClient.put(API_ENDPOINTS.DELETE_MESSAGE, data)).data
    .result as ChatMessageResponse;
};

export const updateChatMessage = async (
  data: ChatMessageUpdateRequest
): Promise<ChatMessageResponse> => {
  return (await httpClient.put(API_ENDPOINTS.UPDATE_MESSAGE, data)).data
    .result as ChatMessageResponse;
};

export const markAsSeen = async (
  conversationId: string
): Promise<ChatMessageResponse> => {
  return await httpClient.put(
    `${API_ENDPOINTS.MARK_AS_SEEN}/${conversationId}`
  );
};
