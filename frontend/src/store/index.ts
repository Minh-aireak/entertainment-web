import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { User, ChatMessage, Conversation, ConversationParticipant, UserFullSummaryResponse } from '../models';
import type { CommentResponse } from '../api/commentService';

// --- Auth Slice ---
interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

const authInitialState: AuthState = {
  user: null,
  isAuthenticated: false,
  // Keep protected routes pending until the HttpOnly-cookie session is restored.
  loading: true,
  error: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState: authInitialState,
  reducers: {
    loginStart: (state) => {
      state.loading = true;
      state.error = null;
    },
    loginSuccess: (state, action: PayloadAction<{ user: User | null }>) => {
      state.loading = false;
      state.user = action.payload.user;
      state.isAuthenticated = true;
    },
    loginFailure: (state, action: PayloadAction<string>) => {
      state.loading = false;
      state.error = action.payload;
    },
    logout: (state) => {
      state.user = null;
      state.isAuthenticated = false;
      state.loading = false;
      state.error = null;
    },
    setUser: (state, action: PayloadAction<User>) => {
      state.user = action.payload;
      state.isAuthenticated = true;
      state.loading = false;
    },
  },
});

// --- Profile Slice ---
interface ProfileState {
  profileData: UserFullSummaryResponse | null;
  loading: boolean;
  lastUpdated: number | null;
}

const profileInitialState: ProfileState = {
  profileData: null,
  loading: false,
  lastUpdated: null,
};

const profileSlice = createSlice({
  name: 'profile',
  initialState: profileInitialState,
  reducers: {
    setProfileData: (state, action: PayloadAction<UserFullSummaryResponse>) => {
      state.profileData = action.payload;
      state.lastUpdated = Date.now();
      state.loading = false;
    },
    setProfileLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
    clearProfileData: (state) => {
      state.profileData = null;
      state.lastUpdated = null;
    },
  },
});

// --- Chat Slice ---
interface ChatState {
  conversations: Conversation[];
  messages: Record<string, ChatMessage[]>;
  activeConversationId: string | null;
  loading: boolean;
  onlineUsers: string[];
}

const chatInitialState: ChatState = {
  conversations: [],
  messages: {},
  activeConversationId: null,
  loading: false,
  onlineUsers: [],
};

const chatSlice = createSlice({
  name: 'chat',
  initialState: chatInitialState,
  reducers: {
    setConversations: (state, action: PayloadAction<Conversation[]>) => {
      state.conversations = action.payload;
    },
    setActiveConversation: (state, action: PayloadAction<string>) => {
      state.activeConversationId = action.payload;
    },
    addMessage: (state, action: PayloadAction<{ conversationId: string; message: ChatMessage }>) => {
      const { conversationId, message } = action.payload;
      if (!state.messages[conversationId]) {
        state.messages[conversationId] = [];
      }
      const existingIndex = state.messages[conversationId].findIndex(item => item.id === message.id);
      if (existingIndex >= 0) {
        state.messages[conversationId][existingIndex] = message;
      } else {
        state.messages[conversationId].push(message);
      }
    },
    setMessages: (state, action: PayloadAction<{ conversationId: string; messages: ChatMessage[] }>) => {
      state.messages[action.payload.conversationId] = action.payload.messages;
    },
    prependMessages: (state, action: PayloadAction<{ conversationId: string; messages: ChatMessage[] }>) => {
      const { conversationId, messages } = action.payload;
      const existing = state.messages[conversationId] || [];
      const existingIds = new Set(existing.map((item) => item.id));
      const deduped = messages.filter((item) => !existingIds.has(item.id));
      state.messages[conversationId] = [...deduped, ...existing];
    },
    updateUserStatus: (state, action: PayloadAction<{ userId: string; status: 'ONLINE' | 'OFFLINE' }>) => {
      const { userId, status } = action.payload;
      if (status === 'ONLINE') {
        if (!state.onlineUsers.includes(userId)) {
          state.onlineUsers.push(userId);
        }
      } else {
        state.onlineUsers = state.onlineUsers.filter((id) => id !== userId);
      }
    },
    updateMessageSeen: (state, action: PayloadAction<{ conversationId: string; userId: string; userName: string; userAvatar: string; lastSeenMessageId: string }>) => {
      const { conversationId, userId, userName, userAvatar, lastSeenMessageId } = action.payload;
      
      const conv = state.conversations.find(c => c.id === conversationId);
      if (conv) {
        if (!conv.participants) conv.participants = [];
        const participant = conv.participants.find((p: ConversationParticipant) => p.userId === userId);
        if (participant) {
          participant.lastSeenMessageId = lastSeenMessageId;
        } else {
          conv.participants.push({
            userId,
            displayName: userName,
            avatar: userAvatar,
            lastSeenMessageId
          });
        }
      }
    },
  },
});

// --- Comment Slice ---
// State keyed by "groupId" - a post/film's sourceId for its top-level comments, or a top-level
// comment's id for the flat list of replies under it. Same shape either way, mirroring the chat
// slice's per-conversationId map. Comments arrive here either from a REST list fetch
// (setComments/appendComments) or from the "comment:created"/"comment:updated"/"comment:deleted"
// realtime broadcasts relayed via the Outbox -> Kafka -> socket-service pipeline. Both paths
// converge on the same array so the UI never has two sources of truth.
interface CommentState {
  comments: Record<string, CommentResponse[]>;
}

const commentInitialState: CommentState = {
  comments: {},
};

const commentSlice = createSlice({
  name: 'comment',
  initialState: commentInitialState,
  reducers: {
    setComments: (state, action: PayloadAction<{ groupId: string; comments: CommentResponse[] }>) => {
      state.comments[action.payload.groupId] = action.payload.comments;
    },
    appendComments: (state, action: PayloadAction<{ groupId: string; comments: CommentResponse[] }>) => {
      const { groupId, comments } = action.payload;
      const existing = state.comments[groupId] || [];
      const existingIds = new Set(existing.map((c) => c.id));
      state.comments[groupId] = [...existing, ...comments.filter((c) => !existingIds.has(c.id))];
    },
    addComment: (state, action: PayloadAction<{ groupId: string; comment: CommentResponse }>) => {
      const { groupId, comment } = action.payload;
      const existing = state.comments[groupId] || [];
      if (existing.some((c) => c.id === comment.id)) return;
      state.comments[groupId] = [comment, ...existing];
    },
    // Used for the "comment:updated" broadcast, which never carries myReaction (per-viewer,
    // stripped by socket-service) - preserve whatever this client already knew about its own
    // reaction instead of letting the broadcast blank it out.
    replaceComment: (state, action: PayloadAction<{ groupId: string; comment: CommentResponse }>) => {
      const { groupId, comment } = action.payload;
      const existing = state.comments[groupId];
      if (!existing) return;
      const index = existing.findIndex((c) => c.id === comment.id);
      if (index >= 0) {
        const previousMyReaction = existing[index].myReaction;
        existing[index] = { ...comment, myReaction: comment.myReaction ?? previousMyReaction };
      }
    },
    removeComment: (state, action: PayloadAction<{ groupId: string; commentId: string }>) => {
      const { groupId, commentId } = action.payload;
      const existing = state.comments[groupId];
      if (!existing) return;
      state.comments[groupId] = existing.filter((c) => c.id !== commentId);
    },
    incrementReplyCount: (state, action: PayloadAction<{ groupId: string; commentId: string }>) => {
      const existing = state.comments[action.payload.groupId];
      const target = existing?.find((c) => c.id === action.payload.commentId);
      if (target) target.replyCount += 1;
    },
    decrementReplyCount: (state, action: PayloadAction<{ groupId: string; commentId: string }>) => {
      const existing = state.comments[action.payload.groupId];
      const target = existing?.find((c) => c.id === action.payload.commentId);
      if (target) target.replyCount = Math.max(0, target.replyCount - 1);
    },
    // Applied right after the POST /reactions REST response, for the acting viewer only -
    // not routed through the realtime broadcast (reactions are high-frequency; see the
    // reaction endpoint's Redis-backed hot path on the backend).
    patchComment: (
      state,
      action: PayloadAction<{ groupId: string; commentId: string; patch: Partial<CommentResponse> }>,
    ) => {
      const existing = state.comments[action.payload.groupId];
      const target = existing?.find((c) => c.id === action.payload.commentId);
      if (target) Object.assign(target, action.payload.patch);
    },
  },
});

// --- Itinerary Slice — DISABLED (no longer used after Post System rewrite)
// interface ItineraryState {
//   itineraries: TravelItinerary[];
//   loading: boolean;
//   error: string | null;
// }
//
// const itineraryInitialState: ItineraryState = {
//   itineraries: [],
//   loading: false,
//   error: null,
// };
//
// const itinerarySlice = createSlice({
//   name: 'itinerary',
//   initialState: itineraryInitialState,
//   reducers: {
//     fetchStart: (state) => {
//       state.loading = true;
//     },
//     fetchSuccess: (state, action: PayloadAction<TravelItinerary[]>) => {
//       state.loading = false;
//       state.itineraries = action.payload;
//     },
//     fetchFailure: (state, action: PayloadAction<string>) => {
//       state.loading = false;
//       state.error = action.payload;
//     },
//     addItinerary: (state, action: PayloadAction<TravelItinerary>) => {
//       state.itineraries.push(action.payload);
//     },
//     updateItinerary: (state, action: PayloadAction<TravelItinerary>) => {
//       const index = state.itineraries.findIndex(i => i.id === action.payload.id);
//       if (index !== -1) {
//         state.itineraries[index] = action.payload;
//       }
//     },
//     deleteItinerary: (state, action: PayloadAction<string>) => {
//       state.itineraries = state.itineraries.filter(i => i.id !== action.payload);
//     },
//   },
// });

// --- UI Slice ---
interface UIState {
  themeMode: 'light' | 'dark';
}

const uiInitialState: UIState = {
  themeMode: (localStorage.getItem('themeMode') as 'light' | 'dark') || 'dark',
};

const uiSlice = createSlice({
  name: 'ui',
  initialState: uiInitialState,
  reducers: {
    toggleThemeMode: (state) => {
      state.themeMode = state.themeMode === 'light' ? 'dark' : 'light';
      localStorage.setItem('themeMode', state.themeMode);
    },
  },
});

// --- Exports ---
export const { loginStart, loginSuccess, loginFailure, logout, setUser } = authSlice.actions;
export const { setProfileData, setProfileLoading, clearProfileData } = profileSlice.actions;
export const { setConversations, setActiveConversation, addMessage, setMessages, prependMessages, updateUserStatus, updateMessageSeen } = chatSlice.actions;
export const { setComments, appendComments, addComment, replaceComment, removeComment, incrementReplyCount, decrementReplyCount, patchComment } = commentSlice.actions;
// export const { fetchStart, fetchSuccess, fetchFailure, addItinerary, updateItinerary, deleteItinerary } = itinerarySlice.actions;
export const { toggleThemeMode } = uiSlice.actions;

export const store = configureStore({
  reducer: {
    auth: authSlice.reducer,
    profile: profileSlice.reducer,
    chat: chatSlice.reducer,
    comment: commentSlice.reducer,
    // itinerary: itinerarySlice.reducer,
    ui: uiSlice.reducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
