import React, { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

interface Notification {
  message: string;
  timestamp: string;
}

const NotificationDisplay: React.FC = () => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [stompClient, setStompClient] = useState<Stomp.Client | null>(null);

  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws'); // Thay đổi URL này nếu notification-service chạy ở cổng khác
    const client = Stomp.over(socket);

    client.connect({}, (frame) => {
      console.log('Connected: ' + frame);
      setStompClient(client);

      // Đăng ký nhận thông báo cho người dùng hiện tại
      // Cần thay thế 'YOUR_USER_ID' bằng ID thực của người dùng đã đăng nhập
      client.subscribe('/user/queue/notifications', (message) => {
        const newNotification: Notification = JSON.parse(message.body);
        setNotifications((prevNotifications) => [...prevNotifications, newNotification]);
      });
    });

    return () => {
      if (stompClient) {
        stompClient.disconnect(() => {
          console.log('Disconnected');
        });
      }
    };
  }, []); // Chạy một lần khi component mount

  return (
    <div style={{ position: 'fixed', top: '10px', right: '10px', zIndex: 1000 }}>
      {notifications.map((notification, index) => (
        <div key={index} style={{
          backgroundColor: '#333',
          color: 'white',
          padding: '10px',
          margin: '5px 0',
          borderRadius: '5px',
          boxShadow: '0 2px 5px rgba(0,0,0,0.2)'
        }}>
          <p>{notification.message}</p>
          <small>{new Date(notification.timestamp).toLocaleTimeString()}</small>
        </div>
      ))}
    </div>
  );
};

export default NotificationDisplay;