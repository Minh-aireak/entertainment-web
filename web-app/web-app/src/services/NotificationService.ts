import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type { NotificationPageResponse } from "../InterfaceDataType/DataType";

export const getMyNotifications = async (
  page: number,
  size: number
): Promise<NotificationPageResponse> => {
  return (
    await httpClient.get(API_ENDPOINTS.GET_MY_NOTIFICATIONS, {
      params: { page, size },
    })
  ).data.result as NotificationPageResponse;
};

