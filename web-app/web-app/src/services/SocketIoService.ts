import { io, Socket } from "socket.io-client";
import AuthService from "../features/client-store/AuthClientStore";

class SocketIoService {
  private socket: Socket | null = null;
  private eventHandlers: Map<string, ((data: any) => void)[]> = new Map(); 
  private currentUserId: string | null = null;

  public connect() {
    if (this.socket && this.socket.connected) {
      console.log("Socket already connected.");
      return;
    }

    console.log("🔌 Initializing socket.io connection...");
    this.socket = io(
      "http://localhost:8099?token=" + AuthService.getAccessToken(),
      {
        transports: ["websocket", "polling"],
        autoConnect: true,
      }
    );

    this.socket.on("connect", () => {
      console.log("✅ Socket.io connected!");
    });

    this.socket.on("disconnect", () => {
      console.log("❌ Socket.io disconnected!"); 
    });

    this.socket.on("message", (data: any) => {
      this.messageHandlers.forEach(handler => handler(data));
    });
  }

  public setUserId(userId: string | null) {
    this.currentUserId = userId;
  }

  public disconnect() {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
      this.eventHandlers.clear();
      this.currentUserId = null;
    }
  }

  public on(eventName: string, handler: (data: any) => void) {
    if (!this.eventHandlers.has(eventName)) {
      this.eventHandlers.set(eventName, []);
    }
    this.eventHandlers.get(eventName)?.push(handler);
  }

  public off(eventName: string, handler: (data: any) => void) {
    const handlers = this.eventHandlers.get(eventName);
    if (handlers) {
      this.eventHandlers.set(eventName, handlers.filter(h => h !== handler));
    }
  }

  public emit(event: string, data: any) {
    if (this.socket && this.socket.connected) {
      this.socket.emit(event, data);
    }
    else {
      console.warn("Socket not connected, cannot send message.");
    }
  }
}

export const socketIoService = new SocketIoService();