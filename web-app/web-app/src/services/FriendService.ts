import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  FriendResponsePerPage,
  UpdateFriendRequestStatus,
  UpdateRelationshipStatus,
} from "../InterfaceDataType/DataType";

export const sendFriendRequest = async (toUserId: string): Promise<string> => {
  return (
    await httpClient.post(`${API_ENDPOINTS.SEND_FRIEND_REQUEST}/${toUserId}`)
  ).data.message as string;
};

export const updateFriendRequestStatus = async (
  request: UpdateFriendRequestStatus
): Promise<string> => {
  return (await httpClient.put(API_ENDPOINTS.UPDATE_FRIEND_REQUEST, request))
    .data.message as string;
};

export const updateRelationshipStatus = async (
  request: UpdateRelationshipStatus
): Promise<string> => {
  return (await httpClient.put(API_ENDPOINTS.UPDATE_RELATIONSHIP, request)).data
    .message as string;
};

export const getListFriend = async (
  page: number,
  size: number
): Promise<FriendResponsePerPage> => {
  return (
    await httpClient.get(API_ENDPOINTS.GET_MY_FRIENDS, {
      params: { page, size },
    })
  ).data.result as FriendResponsePerPage;
};

export const getListFriendRequest = async (
  page: number,
  size: number
): Promise<FriendResponsePerPage> => {
  return (
    await httpClient.get(API_ENDPOINTS.GET_FRIEND_REQUESTS, {
      params: { page, size },
    })
  ).data.result as FriendResponsePerPage;
};
