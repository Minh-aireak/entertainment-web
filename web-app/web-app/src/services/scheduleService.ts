import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  ScheduleResponse,
  ScheduleDetailResponse,
  DeleteScheduleResponse,
  CreatePostResponse,
} from "../InterfaceDataType/DataTypeResponse";

export const getMySchedules = async (page: number) => {
  return await httpClient.get<ScheduleResponse>(API_ENDPOINTS.MY_POSTS, {
    params: {
      page: page,
      size: 6,
    },
  });
};

// export const getSchedule = async (postId: string) => {
//   return await httpClient.get<ScheduleDetailResponse>(
//     `${API_ENDPOINTS.MY_POST}/${postId}`
//   );
// };

export const createSchedule = async (scheduleData: {
  title: string;
  startTime: string;
  endTime: string;
  content?: string;
}) => {
  return await httpClient.post<CreatePostResponse>(
    API_ENDPOINTS.CREATE_POST,
    scheduleData
  );
};

export const updateSchedule = async (
  scheduleId: string,
  scheduleData: {
    title?: string;
    startTime?: string;
    endTime?: string;
    content?: string;
  }
) => {
  return await httpClient.post(
    `${API_ENDPOINTS.UPDATE_POST}/${scheduleId}`,
    scheduleData
  );
};

export const deleteSchedule = async (scheduleId: string) => {
  return await httpClient.delete<DeleteScheduleResponse>(
    `${API_ENDPOINTS.DELETE_POST}/${scheduleId}`
  );
};
