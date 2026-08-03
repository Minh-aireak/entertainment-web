import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { User, ChatMessage, Conversation, TravelItinerary, ConversationParticipant, UserFullSummaryResponse, FilmAggregateResponse } from '../models';

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

// --- Film Slice ---
import type { PageResponse, FilmSummaryResponse } from '../models';

interface FilmState {
  aggregateData: FilmAggregateResponse | null;
  nowPlayingFilms: PageResponse<FilmSummaryResponse> | null;
  loading: boolean;
}

const filmInitialState: FilmState = {
  aggregateData: null,
  nowPlayingFilms: null,
  loading: false,
};

const filmSlice = createSlice({
  name: 'film',
  initialState: filmInitialState,
  reducers: {
    setFilmAggregateData: (state, action: PayloadAction<FilmAggregateResponse>) => {
      state.aggregateData = action.payload;
      state.loading = false;
    },
    setNowPlayingFilms: (state, action: PayloadAction<PageResponse<FilmSummaryResponse>>) => {
      state.nowPlayingFilms = action.payload;
    },
    setFilmLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
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

// --- Itinerary Slice ---
interface ItineraryState {
  itineraries: TravelItinerary[];
  loading: boolean;
  error: string | null;
}

const itineraryInitialState: ItineraryState = {
  itineraries: [],
  loading: false,
  error: null,
};

const itinerarySlice = createSlice({
  name: 'itinerary',
  initialState: itineraryInitialState,
  reducers: {
    fetchStart: (state) => {
      state.loading = true;
    },
    fetchSuccess: (state, action: PayloadAction<TravelItinerary[]>) => {
      state.loading = false;
      state.itineraries = action.payload;
    },
    fetchFailure: (state, action: PayloadAction<string>) => {
      state.loading = false;
      state.error = action.payload;
    },
    addItinerary: (state, action: PayloadAction<TravelItinerary>) => {
      state.itineraries.push(action.payload);
    },
    updateItinerary: (state, action: PayloadAction<TravelItinerary>) => {
      const index = state.itineraries.findIndex(i => i.id === action.payload.id);
      if (index !== -1) {
        state.itineraries[index] = action.payload;
      }
    },
    deleteItinerary: (state, action: PayloadAction<string>) => {
      state.itineraries = state.itineraries.filter(i => i.id !== action.payload);
    },
  },
});

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
export const { setConversations, setActiveConversation, addMessage, setMessages, updateUserStatus, updateMessageSeen } = chatSlice.actions;
export const { fetchStart, fetchSuccess, fetchFailure, addItinerary, updateItinerary, deleteItinerary } = itinerarySlice.actions;
export const { setFilmAggregateData, setNowPlayingFilms, setFilmLoading } = filmSlice.actions;
export const { toggleThemeMode } = uiSlice.actions;

export const store = configureStore({
  reducer: {
    auth: authSlice.reducer,
    profile: profileSlice.reducer,
    chat: chatSlice.reducer,
    itinerary: itinerarySlice.reducer,
    film: filmSlice.reducer,
    ui: uiSlice.reducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
