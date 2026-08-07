import axiosInstance from './axiosInstance';
import type {
  ApiResponse,
  PageResponse,
  CreateRoomRequest,
  JoinRoomRequest,
  PlaybackUpdateRequest,
  RoomMessageCreateRequest,
  RoomResponse,
  RoomListItemResponse,
  RoomParticipantResponse,
  RoomMessageResponse,
} from '../models';

const ROOM_BASE_URL = '/rooms';

export const roomService = {
  createRoom: async (request: CreateRoomRequest): Promise<ApiResponse<RoomResponse>> => {
    const response = await axiosInstance.post(ROOM_BASE_URL, request);
    return response.data;
  },

  listPublicRooms: async (page: number = 1, size: number = 12): Promise<ApiResponse<PageResponse<RoomListItemResponse>>> => {
    const response = await axiosInstance.get(`${ROOM_BASE_URL}/public`, { params: { page, size } });
    return response.data;
  },

  listMyRooms: async (page: number = 1, size: number = 12): Promise<ApiResponse<PageResponse<RoomListItemResponse>>> => {
    const response = await axiosInstance.get(`${ROOM_BASE_URL}/my`, { params: { page, size } });
    return response.data;
  },

  getRoom: async (roomId: string): Promise<ApiResponse<RoomResponse>> => {
    const response = await axiosInstance.get(`${ROOM_BASE_URL}/${roomId}`);
    return response.data;
  },

  joinRoom: async (roomId: string, request?: JoinRoomRequest): Promise<ApiResponse<RoomResponse>> => {
    const response = await axiosInstance.post(`${ROOM_BASE_URL}/${roomId}/join`, request ?? {});
    return response.data;
  },

  leaveRoom: async (roomId: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.post(`${ROOM_BASE_URL}/${roomId}/leave`);
    return response.data;
  },

  closeRoom: async (roomId: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.delete(`${ROOM_BASE_URL}/${roomId}`);
    return response.data;
  },

  updatePlayback: async (roomId: string, request: PlaybackUpdateRequest): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.patch(`${ROOM_BASE_URL}/${roomId}/playback`, request);
    return response.data;
  },

  listParticipants: async (roomId: string): Promise<ApiResponse<RoomParticipantResponse[]>> => {
    const response = await axiosInstance.get(`${ROOM_BASE_URL}/${roomId}/participants`);
    return response.data;
  },

  listMessages: async (roomId: string, page: number = 1, size: number = 30): Promise<ApiResponse<PageResponse<RoomMessageResponse>>> => {
    const response = await axiosInstance.get(`${ROOM_BASE_URL}/${roomId}/messages`, { params: { page, size } });
    return response.data;
  },

  sendMessage: async (roomId: string, request: RoomMessageCreateRequest): Promise<ApiResponse<RoomMessageResponse>> => {
    const response = await axiosInstance.post(`${ROOM_BASE_URL}/${roomId}/messages`, request);
    return response.data;
  },
};
