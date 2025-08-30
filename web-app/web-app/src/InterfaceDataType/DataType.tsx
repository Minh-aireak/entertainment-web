export interface UserProfileResponse {
  code: number;
  result: {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    dob: Date;
    phoneNumber: string;
    city: string;
    joinDate: Date;
    avatar?: string;
  };
}

export interface LoginResponse {
  code: number;
  result: {
    token: string;
  };
}

export interface UpdateProfileResponse {
  code: number;
  message: string;
}

export interface Schedule {
  id: string;
  userId: string;
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
  createdDate: Date;
  modifiedDate: Date;
  status: string;
}

export interface ScheduleResponse {
  id: string;
  userId: string;
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
  createdDate: string;
  modifiedDate: Date;
  status: string;
}

export interface SchedulePageResponse {
  code: number;
  result: {
    currentPage: number;
    totalPages: number;
    pageSize: number;
    totalElements: number;
    data: ScheduleResponse[];
  };
}

export interface ScheduleDetailResponse {
  code: number;
  result: ScheduleResponse;
}

export interface DeleteScheduleResponse {
  code: number;
  message: string;
}

export interface CreatePostResponse {
  code: number;
  message: string;
  result: ScheduleResponse;
}

export interface StatusUpdateResponse {
  userId: string;
  scheduleId: string;
  title: string;
  newStatus: string;
  message: string;
}

export interface UpdatePostResponse {
  code: number;
  result: ScheduleResponse;
  message: string;
}

export interface PostDataUpdate {
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
}
