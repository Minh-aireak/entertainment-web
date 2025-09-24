import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  SchedulePageResponse,
  CreatePostResponse,
  ScheduleDetailResponse,
  UpdatePostData,
  UpdatePostResponse,
} from "../InterfaceDataType/DataType";

export const getMySchedules = async (
  page: number,
  size: number,
  type: string
) => {
  return await httpClient.get<SchedulePageResponse>(API_ENDPOINTS.MY_POSTS, {
    params: {
      page: page,
      size: size,
      type: type,
    },
  });
};

export const getSchedule = async (postId: string) => {
  return await httpClient.get<ScheduleDetailResponse>(
    `${API_ENDPOINTS.MY_POST}/${postId}`
  );
};

export const createSchedule = async (scheduleData: {
  postType: string;
  title: string;
  startTime: Date;
  endTime: Date;
  content: string;
  latStart: string;
  lonStart: string;
  latEnd: string;
  lonEnd: string;
}) => {
  return await httpClient.post<CreatePostResponse>(
    API_ENDPOINTS.CREATE_POST,
    scheduleData
  );
};

export const updateSchedule = async (
  scheduleId: string,
  type: string,
  scheduleData: UpdatePostData
) => {
  return await httpClient.put<UpdatePostResponse>(
    `${API_ENDPOINTS.UPDATE_POST}/${scheduleId}`,
    scheduleData,
    {
      params: { type: type },
    }
  );
};

export const deleteSchedule = async (scheduleId: string, type: string) => {
  return await httpClient.delete(`${API_ENDPOINTS.DELETE_POST}/${scheduleId}`, {
    params: {
      type: type,
    },
  });
};
