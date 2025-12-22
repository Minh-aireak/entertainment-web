import { io, Socket } from "socket.io-client";

let socket: Socket | null = null;

export function connectSocket(token: string) {
  if (socket && socket.connected) return socket;

  socket = io("http://localhost:8099?token=" + token, {
    transports: ["websocket", "polling"],
    autoConnect: true,
  });

  socket.on("connect", () => {
    console.log("Socket connected", socket?.id);
  });

  socket.on("connect_error", (err) => {
    console.error("Socket connect_error", err.message);
  });

  socket.on("notification", (data) => {
    <NotificationBell />;
  });

  return socket;
}

export function disconnectSocket() {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
}

export function on(event: string, handler: (data: any) => void) {
  socket?.on(event, handler);
}

export function off(event: string, handler: (data: any) => void) {
  socket?.off(event, handler);
}

export function emit(event: string, data: any) {
  if (socket && socket.connected) {
    socket.emit(event, data);
  }
}

// class SocketIoService {
//   private socket: Socket | null = null;
//   private eventHandlers: Map<string, ((data: any) => void)[]> = new Map();
//   private currentUserId: string | null = null;

//   public connect() {
//     this.socket.on("message", (data: any) => {
//       this.messageHandlers.forEach((handler) => handler(data));
//     });
//   }

//   public setUserId(userId: string | null) {
//     this.currentUserId = userId;
//   }

//   public disconnect() {
//     if (this.socket) {
//       this.socket.disconnect();
//       this.socket = null;
//       this.eventHandlers.clear();
//       this.currentUserId = null;
//     }
//   }

//   public on(eventName: string, handler: (data: any) => void) {
//     if (!this.eventHandlers.has(eventName)) {
//       this.eventHandlers.set(eventName, []);
//     }
//     this.eventHandlers.get(eventName)?.push(handler);
//   }

//   public off(eventName: string, handler: (data: any) => void) {
//     const handlers = this.eventHandlers.get(eventName);
//     if (handlers) {
//       this.eventHandlers.set(
//         eventName,
//         handlers.filter((h) => h !== handler)
//       );
//     }
//   }

//   public emit(event: string, data: any) {
//     if (this.socket && this.socket.connected) {
//       this.socket.emit(event, data);
//     } else {
//       console.warn("Socket not connected, cannot send message.");
//     }
//   }
// }

// export const socketIOService = new SocketIOService();
