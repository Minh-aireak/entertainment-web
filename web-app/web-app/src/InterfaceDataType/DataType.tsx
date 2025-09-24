export interface UserProfileResponse {
  code: number;
  result: {
    userId: string;
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
  postType: string;
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
  postType: string;
  userId: string;
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
  createdDate: string;
  modifiedDate: Date;
  status: string;
  startPosition: DataWeatherResponse;
  endPosition: DataWeatherResponse;
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

export interface PostData {
  postType: string;
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
  latStart: string;
  lonStart: string;
  latEnd: string;
  lonEnd: string;
}

export interface UpdatePostData {
  title: string;
  content: string;
  startTime: Date;
  endTime: Date;
  latStart: string;
  lonStart: string;
  latEnd: string;
  lonEnd: string;
}

export interface LatLon {
  lat: string;
  lon: string;
}

export interface Main {
  temp: number;
  feels_like: number;
  humidity: number;
}

export interface Weather {
  main: string;
  description: string;
  icon: string;
}

export interface Clouds {
  all: number;
}

export interface Wind {
  speed: number;
  gust: number;
}

export interface Rain {
  rain: number;
}

export interface Sys {
  pod: string;
}

export interface ListForecast {
  main: Main;
  weather: Weather[];
  clouds: Clouds;
  wind: Wind;
  pop: number;
  rain: Rain;
  sys: Sys;
  dt_txt: string;
}

export interface City {
  name: string;
  country: string;
}

export interface DataWeatherResponse {
  city: City;
  list: ListForecast[];
}
