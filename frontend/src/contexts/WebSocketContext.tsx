import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useSelector } from 'react-redux';

import { identityService } from '../api/identityService';
import type { RootState } from '../store';

export const REALTIME_NOTIFICATION_EVENT = 'realtime:notification';
export const REALTIME_FRIENDSHIP_EVENT = 'realtime:friendship';

type SocketEventHandler = (data: any) => void;

interface WebSocketContextValue {
  isConnected: boolean;
  send: (message: object) => boolean;
  subscribe: (eventName: string, handler: SocketEventHandler) => () => void;
}

interface RealtimeNotification {
  type?: string;
  title?: string;
  content?: string;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

const resolveSocketUrl = () => {
  if (import.meta.env.VITE_SOCKET_URL) {
    return import.meta.env.VITE_SOCKET_URL;
  }

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888/api/v1';
  const apiUrl = new URL(apiBaseUrl, window.location.origin);
  const protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  const basePath = apiUrl.pathname.replace(/\/$/, '');
  return `${protocol}//${apiUrl.host}${basePath}/sockets/ws`;
};

export const WebSocketProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const isAuthenticated = useSelector((state: RootState) => state.auth.isAuthenticated);
  const socketRef = useRef<WebSocket | null>(null);
  const handlersRef = useRef<Map<string, Set<SocketEventHandler>>>(new Map());
  const [isConnected, setIsConnected] = useState(false);

  const subscribe = useCallback((eventName: string, handler: SocketEventHandler) => {
    const handlers = handlersRef.current.get(eventName) ?? new Set<SocketEventHandler>();
    handlers.add(handler);
    handlersRef.current.set(eventName, handlers);

    return () => {
      const currentHandlers = handlersRef.current.get(eventName);
      currentHandlers?.delete(handler);
      if (currentHandlers?.size === 0) {
        handlersRef.current.delete(eventName);
      }
    };
  }, []);

  const emit = useCallback((eventName: string, data: any) => {
    handlersRef.current.get(eventName)?.forEach((handler) => handler(data));
  }, []);

  const send = useCallback((message: object) => {
    if (socketRef.current?.readyState !== WebSocket.OPEN) {
      return false;
    }

    socketRef.current.send(JSON.stringify(message));
    return true;
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      socketRef.current?.close(1000, 'User logged out');
      socketRef.current = null;
      setIsConnected(false);
      return;
    }

    let disposed = false;
    let reconnectAttempt = 0;
    let refreshedAfterAuthClose = false;
    let reconnectTimer: number | undefined;

    const scheduleReconnect = () => {
      if (disposed) return;
      const delay = Math.min(1000 * (2 ** reconnectAttempt), 10_000);
      reconnectAttempt += 1;
      reconnectTimer = window.setTimeout(connect, delay);
    };

    const connect = () => {
      if (disposed) return;

      const socket = new WebSocket(resolveSocketUrl());
      socketRef.current = socket;

      socket.onopen = () => {
        reconnectAttempt = 0;
        refreshedAfterAuthClose = false;
        setIsConnected(true);
      };

      socket.onmessage = (messageEvent) => {
        try {
          const payload = JSON.parse(messageEvent.data) as { event?: string; data?: any };
          if (!payload.event) return;

          if (payload.event === 'notification') {
            const notification = payload.data as RealtimeNotification;
            window.dispatchEvent(new CustomEvent(REALTIME_NOTIFICATION_EVENT, {
              detail: notification,
            }));
            if (notification.type === 'FRIEND_REQUEST' || notification.type === 'FRIEND_ACCEPTED') {
              window.dispatchEvent(new CustomEvent(REALTIME_FRIENDSHIP_EVENT, {
                detail: notification,
              }));
            }
            window.dispatchEvent(new Event('sidebar-counts:refresh'));
          } else if (payload.event === 'new-notification') {
            window.dispatchEvent(new Event('sidebar-counts:refresh'));
          }

          emit(payload.event, payload.data);
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };

      socket.onerror = (error) => {
        console.error('WebSocket error:', error);
      };

      socket.onclose = async (closeEvent) => {
        if (socketRef.current === socket) {
          socketRef.current = null;
        }
        setIsConnected(false);
        if (disposed) return;

        if ((closeEvent.code === 1007 || closeEvent.code === 1008) && !refreshedAfterAuthClose) {
          refreshedAfterAuthClose = true;
          try {
            const response = await identityService.refresh();
            if (!disposed && response.code === 1000) {
              connect();
              return;
            }
          } catch (error) {
            console.error('Unable to refresh session for WebSocket reconnect:', error);
          }
        }

        scheduleReconnect();
      };
    };

    connect();

    return () => {
      disposed = true;
      if (reconnectTimer !== undefined) {
        window.clearTimeout(reconnectTimer);
      }
      socketRef.current?.close(1000, 'WebSocket provider unmounted');
      socketRef.current = null;
      setIsConnected(false);
    };
  }, [emit, isAuthenticated]);

  const value = useMemo(() => ({ isConnected, send, subscribe }), [isConnected, send, subscribe]);

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocket must be used inside WebSocketProvider');
  }
  return context;
};
