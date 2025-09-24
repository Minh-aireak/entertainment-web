export const CONFIG = {
  API_GATE_WAY: "http://localhost:8888/api/v1",
};

export const API_ENDPOINTS = {
  LOGIN: "/identity/auth/login",
  REFRESH_TOKEN: "/identity/auth/refresh",
  REGISTER: "/identity/users/registration",
  MY_INFO: "/profile/get-my-info",
  UPDATE_PROFILE: "/profile/update-my-profile",
  DELETE_PROFILE: "/profile/delete",
  CREATE_POST: "/post/create",
  MY_POST: "/post/get-post",
  MY_POSTS: "/post/get-my-posts",
  UPDATE_POST: "/post/update-post",
  DELETE_POST: "/post/delete",
  UPDATE_AVATAR: "/profile/upload-avatar",
  GET_DATA_WEATHER: "/weather/get-data-weather",

  CREATE_CONVERSATION: "/chat/conversations/create",
  CREATE_MESSAGE: "/chat/messages/create",
  GET_CONVERSATION_MESSAGES: "/chat/messages",
  MY_CONVERSATIONS: "/chat/conversations/my-conversations",
  SEARCH_USER: "/profile/users/search",

  // SCHEDULE_DETAIL: "/schedule/getScheduleById",
};

export const OAuthConfig = {
  clientId:
    "266132262326-islopun9fjajf96gds894ncmi43ugcku.apps.googleusercontent.com",
  redirectUri: "http://localhost:5173/authenticate",
  authUri: "https://accounts.google.com/o/oauth2/auth",
};
