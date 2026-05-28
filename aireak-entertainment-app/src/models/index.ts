export interface UserFullSummaryResponse {
  userId: string;
  username: string;
  email: string;
  displayName: string;
  dob: string;
  phoneNumber: string;
  city: string;
  joinDate: string;
  avatar: string;
  totalPost: number | null;
  totalFriends: number | null;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  messageType: MessageType;
  content: string;
  seq: number;
  attachmentFileUrl?: string;
  replyToMessageId?: string;
  clientMessageId?: string;
  createdDate: string;
  modifiedDate?: string;
  messageStatus: MessageStatus;
}

export interface Conversation {
  id: string;
  type: ConversationType;
  createdDate: string;
  modifiedDate?: string;
  totalSeq: number;
  userIds: string[];
  lastMessageId?: string;
  deleted: boolean;
}

export interface ConversationDirect extends Conversation {
  participantsHash: string;
}

export interface ConversationGroup extends Conversation {
  groupName: string;
  groupOwner: string;
  groupAvatar?: string;
}

export interface UnreadCountResponse {
  data: Record<string, number>;
  total: number;
}

export interface ConversationMember {
  id: string;
  conversationId: string;
  userId: string;
  lastSeenMessageId?: string;
  lastSeenSeq?: number;
  lastSeenAt?: string;
}

export interface UserProfile {
  userId: string;
  username: string;
  email: string;
  displayName?: string;
  firstName: string;
  lastName: string;
  dob: string;
  phoneNumber?: string;
  city: string;
  joinDate?: string;
  avatar?: string;
}

export interface FileMgmt {
  id: string;
  ownerId: string;
  contentType: string;
  size: number;
  md5Checksum: string;
  path: string;
}

export interface Notification {
  id: string;
  type: TypeNotification;
  userIdSender: string;
  toUserIds: string[];
  recipientReadMap?: Record<string, string>;
  message: string;
  createdAt: string;
}

export interface WebSocketSession {
  id: string;
  socketSessionId: string;
  userId: string;
  createdAt: string;
}

export type PostType =
  | "BUSINESS_SCHEDULE"
  | "TRAVEL_ITINERARY";

export interface Post {
  id: string;
  postType: PostType;
  userId: string;
  title: string;
  content: string;
  startTime?: string;
  endTime?: string;
  createdDate: string;
  modifiedDate?: string;
  status?: string;
  startJobKey?: string;
  endJobKey?: string;
  listUsersJoin?: string[];
}

export interface ActionConfig {
  requiredStatus: string;
  newStatus: string;
  messageTemplate: string;
}

export interface TravelItinerary extends Post {
  startPosition?: DataWeatherResponse;
  endPosition?: DataWeatherResponse;
}

export interface FriendRequestResponse {
  senderId: string;
  displayName: string;
  avatar: string;
  status: FriendRequestStatus;
}

export interface UserRelationshipResponse {
  friendId: string;
  displayName: string;
  friendAvatar: string;
  status: RelationshipStatus;
}

export type RelationshipStatus =
  | "FRIEND"
  | "UNFRIEND"
  | "BLOCKED_FROM_SENDER"
  | "BLOCKED_FROM_RECEIVER";

export type FriendRequestStatus =
  | "PENDING"
  | "ACCEPTED"
  | "CANCEL";

export interface Comment {
  id: string;
  sourceId: string;
  userId: string;
  content: string;
  parentId?: string;
  topParentId?: string;
  likeCount: number;
  replyCount: number;
  status: CommentStatus;
  createdDate: string;
  modifiedDate?: string;
}

export interface Sender {
  name: string;
  email: string;
}

export type CommentStatus =
  | "ORIGINAL"
  | "EDITED"
  | "DELETED";

export interface User {
  id: string;
  username: string;
  password?: string;
  email: string;
  roles: Role[];
}

export interface Role {
  name: string;
  description: string;
  permissions: Permission[];
}

export interface Permission {
  name: string;
  description: string;
}

export interface TokenRequest {
  token: string;
}

export interface BulkUserProfileRequest {
  userIds: Set<string>;
}

export interface EmailRequest {
  channel: string;
  recipient: string;
  templateCode: string;
  params: Record<string, any>;
  subject: string;
  body: string;
}

export interface NotificationRequest {
  typeNotification: TypeNotification;
  userIdSender: string;
  toUserIds: string[];
  metadata: Record<string, any>;
}

export interface ChatMessageCreateRequest {
  conversationId: string;
  messageType: MessageType;
  content: string;
  attachmentFileUrl?: string;
  replyToMessageId?: string;
  clientMessageId?: string;
}

export interface ChatMessageDeleteRequest {
  chatMessageId: string;
  conversationId: string;
}

export interface ChatMessageUpdateRequest {
  chatMessageId: string;
  content: string;
}

export interface CreateCommentRequest {
  sourceId: string;
  content: string;
  parentId?: string;
  topParentId?: string;
  listIdsJoin?: string[];
}

export interface DataWeatherRequest {
  lat: string;
  lon: string;
}

export interface UserProfileUpdateRequest {
  firstName: string;
  lastName: string;
  email: string;
  displayName: string;
  dob: string;
  phoneNumber: string;
  city: string;
  avatar?: string;
}

export interface ScheduleRequest {
  postType: string;
  title: string;
  content: string;
  startTime: string;
  endTime: string;
  listUsersJoin?: string[];
  latStart: string;
  lonStart: string;
  latEnd: string;
  lonEnd: string;
}

export interface ScheduleUpdateRequest {
  title: string;
  content: string;
  startTime: string;
  endTime: string;
  latStart: string;
  latEnd: string;
  lonStart: string;
  lonEnd: string;
}

export interface AuthenticationRequest {
  username: string;
  password: string;
}

export interface UserCreationRequest {
  username: string;
  password: string;
  email: string;
}

export interface UserProfileCreationRequest {
  userId: string;
  username: string;
  email: string;
  displayName: string;
  fistName: string;
  lastName: string;
  dob: string;
  phoneNumber: string;
  city: string;
  joinDate: string;
}

export interface ChangePasswordRequest {
  oldPassword?: string;
  newPassword?: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  password?: string;
}

export interface ExchangeTokenRequest {
  code: string;
  client_id: string;
  client_secret: string;
  redirect_uri: string;
  grant_type: string;
}

export interface RoleCreationRequest {
  name: string;
  description: string;
  permissions: string[];
}

export interface RoleUpdateRequest {
  name: string;
  description: string;
  permissions: string[];
}

export interface PermissionCreationRequest {
  name: string;
  description: string;
}

export interface PermissionUpdateRequest {
  name: string;
  description: string;
}

export interface ApiResponse<T> {
  code: number;
  result: T;
  message?: string;
}

export interface PageResponse<T> {
  currentPage: number;
  totalPages: number;
  pageSize: number;
  totalElement: number;
  data: T[];
}

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  roles: RoleResponse[];
}

export interface AuthenticationResponse {
  token: string;
}

export interface IntrospectResponse {
  valid: boolean;
  userId: string
}

export interface ExchangeTokenResponse {
  access_token: string;
  expires_in: string;
  refresh_token: string;
  scope: string;
  token_type: string;
}

export interface OutboundUserResponse {
  id: string;
  email: string;
  verified_email: boolean;
  given_name: string;
  family_name: string;
  picture: string;
  locale: string;
}

export interface RoleResponse {
  name: string;
  description: string;
  permissions: PermissionResponse[];
}

export interface PermissionResponse {
  name: string;
  description: string;
}

export interface ChatMessageResponse {
  id: string;
  conversationId: string;
  me: boolean;
  messageType: MessageType;
  content: string;
  seq: number;
  attachmentFileUrl: string;
  replyToMessageId: string;
  createdDate: string;
  modifiedDate: string;
  messageStatus: MessageStatus;
  senderId: string;
  senderName: string;
  senderAvatar: string;
  clientMessageId: string;
}

export interface ScheduleResponse {
  id: string;
  postType: string;
  userId: string;
  displayName: string;
  avatar: string;
  title: string;
  content: string;
  startTime: string;
  endTime: string;
  createdDate: string;
  modifiedDate: string;
  status: string;
  startPosition?: DataWeatherResponse;
  endPosition?: DataWeatherResponse;
}

export interface StatusResponse {
  quantityOnGoing: number;
  quantityUpComing: number;
}

export interface StatusUpdateResponse {
  userId: string;
  scheduleId: string;
  title: string;
  newStatus: string;
  message: string;
}

export interface DataWeatherResponse {
  list: ListForecast[];
  city: City;
}

export interface ListForecast {
  dt: number;
  main: MainWeather;
  weather: WeatherInfo[];
  clouds: Clouds;
  wind: Wind;
  pop: number;
  rain?: Rain;
  sys: Sys;
  dt_txt: string;
}

export interface MainWeather {
  temp: number;
  feels_like: number;
  humidity: number;
}

export interface WeatherInfo {
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
  "3h": number;
}

export interface Sys {
  pod: string;
}

export interface City {
  name: string;
  country: string;
}

export interface ParticipantResponse {
  userId: string;
  displayName: string;
  avatar: string;
  lastSeenMessageId: string;
}

export interface ConversationResponse {
  id: string;
  type: string;
  userIds: string[];
  totalSeq: number;
  createdDate: string;
  modifiedDate?: string;

  // Direct chat
  participantsHash?: string;
  directName?: string;
  directAvatar?: string;

  // Group chat
  groupName?: string;
  groupOwner?: string;
  groupAvatar?: string;

  lastMessage: string;
  deleted: boolean;
  participants?: ParticipantResponse[];
}

export interface UserProfileResponse {
  userId: string;
  username: string;
  email: string;
  displayName: string;
  firstName: string;
  lastName: string;
  dob: string;
  phoneNumber: string;
  city: string;
  joinDate: string;
  avatar: string;
}

export interface PostResponse {
  id: string;
  userId: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface Recipient {
  name: string;
  email: string;
}

export interface SendEmailRequest {
  to: Recipient[];
  subject: string;
  htmlContent: string;
}

export interface CommentResponse {
  id: string;
  sourceId: string;
  userId: string;
  content: string;
  parentId?: string;
  topParentId?: string;
  likeCount: number;
  replyCount: number;
  durationCreatedDate: string;
}

export interface FriendResponse {
  userId: string;
  username: string;
  status: string;
}

export interface NotificationResponse {
  id: string;
  type: string;
  userIdSender: string;
  displayNameSender: boolean;
  avatarSender: string;
  read: boolean;
  message: string;
  createdAt: string;
}

export interface TravelItineraryResponse {
  id: string;
  userId: string;
  destination: string;
  startDate: string;
  endDate: string;
  activities: string[];
}

export interface WeatherResponse {
  list: {
    dt: number;
    main: {
      temp: number;
      feels_like: number;
      humidity: number;
    };
    weather: {
      main: string;
      description: string;
      icon: string;
    }[];
    wind: {
      speed: number;
    };
    dt_txt: string;
  }[];
  city: {
    name: string;
    country: string;
  };
}

export interface MessageCreatedEvent {
  eventId: string;
  eventType: string;
  timestamp: string;
  producer: string;
  receiverIds: string[];
  message: ChatMessageResponse;
}

export interface UserCreatedEvent {
  userId: string;
  username: string;
  email: string;
  displayName: string;
  firstName: string;
  lastName: string;
  dob: string;
  phoneNumber: string;
  city: string;
  joinDate: string;
}

export interface UserUpdatedEvent {
  userId: string;
  avatar: string;
  displayName: string;
}

export interface ProfileUpdatedEvent {
  userId: string;
  avatar: string;
  displayName: string;
}
    
export interface FriendRequestEvent {
  fromUserId: string;
  toUserId: string;
  hashFriendRequest: string;
  createdAt: string;
}

export interface NotificationSocketData {
  notificationResponse: NotificationResponse;
  userIds: string[];
}

export interface StatusChangeData {
  fromUserId: string;
  listUserIds: string[];
  message: string;
}

export type ConversationType = 'DIRECT' | 'GROUP';

export type MessageStatus = 'SENT' | 'DELIVERED' | 'SEEN' | 'FAILED';

export type MessageType =
  | 'DELETED_FOR_EVERYONE'
  | 'TEXT'
  | 'IMAGE'
  | 'AUDIO'
  | 'VIDEO'
  | 'FILE'
  | 'STICKER'
  | 'POST';

export type TypeNotification =
  | 'NEW_CHAT'
  | 'FRIEND_REQUEST'
  | 'FRIEND_ACCEPTED'
  | 'STATUS_CHANGE'
  | 'POST_COMMENT'
  | 'SOCIAL_LIKE'
  | 'SOCIAL_COMMENT'
  | 'SOCIAL_SHARE'
  | 'FILM_LIKE'
  | 'FILM_COMMENT'
  | 'FILM_SHARE'
  | 'LIKE_POST'
  | 'COMMENT_POST'
  | 'SYSTEM';

export interface FileResponse {
  url: string;
}

export interface FileInfo {
  name: string;
  contentType: string;
  size: number;
  md5Checksum: string;
  path: string;
  url: string;
}
