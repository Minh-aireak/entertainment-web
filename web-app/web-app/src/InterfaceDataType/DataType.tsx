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
    displayName?: string;
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
  displayName: string;
  avatar: string;
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
  displayName: string;
  avatar: string;
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

export interface ParticipantInfo {
  userId: string;
  displayName: string;
  avatar: string;
}

export interface ConversationResponse {
  id: string;
  type: string;
  participantsHash: string;
  participantInfos: ParticipantInfo[];
  directName: string;
  directAvatar: string;
  groupName: string;
  groupOwner: string;
  groupAvatar: string;
  createdDate: Date;
  modifiedDate: Date;
}

export type MessageType =
  | "DELETED_FOR_SENDER"
  | "DELETED_FOR_EVERYONE"
  | "TEXT"
  | "IMAGE"
  | "AUDIO"
  | "VIDEO"
  | "FILE"
  | "STICKER"
  | "POST";
export type MessageStatus =
  | "SENDING"
  | "SENT"
  | "DELIVERED"
  | "SEEN"
  | "FAILED";

export interface ChatMessageCreateRequest {
  conversationId: string;
  messageType: MessageType;
  content: string;
  attachmentFileUrl: string;
  replyToMessageId: string;
}

export interface ChatMessageResponse {
  id: string;
  conversationId: string;
  me: boolean;
  sender: ParticipantInfo;
  messageType: MessageType;
  content: string;
  attachmentFileUrl: string;
  replyToMessageId: string;
  createdDate: Date;
  modifiedDate: Date;
  messageStatus: MessageStatus;
  seenAtMap: Record<string, Date> | null;
}

export interface ChatMessageDeleteRequest {
  chatMessageId: string;
  deleteType: string;
}

export interface ChatMessageUpdateRequest {
  chatMessageId: string;
  content: string;
}

export interface BulkUserProfileRequest {
  userIds: string[];
}

export type FriendRequestStatus = "PENDING" | "ACCEPTED" | "CANCEL";

export interface FriendRequest {
  id: string;
  userId: string;
  toUserId: string;
  hashFriendRequest: string;
  friendRequestStatus: FriendRequestStatus;
  createdAt: Date;
}

export interface UpdateFriendRequestStatus {
  userId: string;
  friendRequestStatus: FriendRequestStatus;
}

export type RelationshipStatus =
  | "FRIEND"
  | "UNFRIEND"
  | "BLOCKED_FROM_SENDER"
  | "BLOCK_FROM_RECEIVER";

export interface UserRelationship {
  id: string;
  userId: string;
  toUserId: string;
  hashFriendRequest: string;
  relationshipStatus: RelationshipStatus;
  createdDate: Date;
}

export interface UpdateRelationshipStatus {
  userId: string;
  friendStatus: RelationshipStatus;
}

export interface FriendResponse {
  userId: string;
  displayName: string;
  avatar: string;
  hash: string;
  date: Date;
  status: RelationshipStatus;
}

export interface FriendResponsePerPage {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalElements: number;
  data: FriendResponse[];
}

export interface NotificationResponse {
  id: string;
  type: string;
  userId: string;
  displayName: string;
  avatar: string;
  isRead: boolean;
  message: string;
  actionUrl: string;
  createdAt: Date;
}

export interface NotificationPageResponse {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalElements: number;
  data: NotificationResponse[];
}

export interface StatusResponse {
  code: number;
  result: {
    quantityOnGoing: number;
    quantityUpComing: number;
  };
}
