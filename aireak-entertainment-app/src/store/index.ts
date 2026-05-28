import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { User, ChatMessage, Conversation, TravelItinerary } from '../models';

// --- Auth Slice ---
interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

const authInitialState: AuthState = {
  user: null,
  token: localStorage.getItem('token'),
  isAuthenticated: !!localStorage.getItem('token'),
  loading: false,
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
    loginSuccess: (state, action: PayloadAction<{ user: User; token: string }>) => {
      state.loading = false;
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
      localStorage.setItem('token', action.payload.token);
    },
    loginFailure: (state, action: PayloadAction<string>) => {
      state.loading = false;
      state.error = action.payload;
    },
    logout: (state) => {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      localStorage.removeItem('token');
    },
    setUser: (state, action: PayloadAction<User>) => {
      state.user = action.payload;
      state.isAuthenticated = true;
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
      state.messages[conversationId].push(message);
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
        const participant = conv.participants.find(p => p.userId === userId);
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

// --- Exports ---
export const { loginStart, loginSuccess, loginFailure, logout, setUser } = authSlice.actions;
export const { setConversations, setActiveConversation, addMessage, setMessages, updateUserStatus, updateMessageSeen } = chatSlice.actions;
export const { fetchStart, fetchSuccess, fetchFailure, addItinerary, updateItinerary, deleteItinerary } = itinerarySlice.actions;

export const store = configureStore({
  reducer: {
    auth: authSlice.reducer,
    chat: chatSlice.reducer,
    itinerary: itinerarySlice.reducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
