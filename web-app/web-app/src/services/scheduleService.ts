import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  SchedulePageResponse,
  CreatePostResponse,
  ScheduleDetailResponse,
  PostDataUpdate,
  UpdatePostResponse,
} from "../InterfaceDataType/DataType";

export const getMySchedules = async (page: number, size: number) => {
  return await httpClient.get<SchedulePageResponse>(API_ENDPOINTS.MY_POSTS, {
    params: {
      page: page,
      size: size,
    },
  });
};

export const getSchedule = async (postId: string) => {
  return await httpClient.get<ScheduleDetailResponse>(
    `${API_ENDPOINTS.MY_POST}/${postId}`
  );
};

export const createSchedule = async (scheduleData: {
  title: string;
  startTime: Date;
  endTime: Date;
  content: string;
}) => {
  return await httpClient.post<CreatePostResponse>(
    API_ENDPOINTS.CREATE_POST, 
    scheduleData
  );
};

export const updateSchedule = async (
  scheduleId: string,
  scheduleData: PostDataUpdate
) => {
  return await httpClient.post<UpdatePostResponse>(
    `${API_ENDPOINTS.UPDATE_POST}/${scheduleId}`,
    scheduleData
  );
};

export const deleteSchedule = async (scheduleId: string) => {
  return await httpClient.delete(`${API_ENDPOINTS.DELETE_POST}/${scheduleId}`);
};
