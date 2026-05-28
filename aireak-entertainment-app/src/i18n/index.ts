import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

const resources = {
  en: {
    translation: {
      welcome: 'Welcome to Travel Planner',
      home: 'Home',
      moduleLabel: 'Module',
      moduleSocial: 'Social',
      moduleMovie: 'Movies',
      moduleSchedule: 'Schedule',
      navigation: 'Navigation',
      trending: 'Trending',
      library: 'Library',
      switchModule: 'Switch module',
      socialHomeTitle: 'Social Hub',
      socialHomeSubtitle: 'Chat, connect with friends, and manage your profile.',
      socialChatDesc: 'Message friends and share experiences in real time.',
      socialFriendsDesc: 'Find and connect with fellow travelers.',
      socialProfileDesc: 'Update your personal information and preferences.',
      travelHomeTitle: 'Travel Planner',
      travelHomeSubtitle: 'Plan itineraries and check weather at your destinations.',
      travelItineraryDesc: 'Build a detailed schedule for your next trip.',
      travelWeatherDesc: 'Stay updated on weather at your destination.',
      explore: 'Explore',
      adminPage: 'Admin',
      login: 'Login',
      register: 'Register',
      logout: 'Logout',
      profile: 'Profile',
      friends: 'Friends',
      chat: 'Chat',
      itinerary: 'Itinerary',
      weather: 'Weather',
      admin: 'Admin',
      settings: 'Settings',
    },
  },
  vi: {
    translation: {
      welcome: 'Chào mừng đến với Travel Planner',
      home: 'Trang chủ',
      moduleLabel: 'Ứng dụng',
      moduleSocial: 'Mạng xã hội',
      moduleMovie: 'Xem phim',
      moduleSchedule: 'Lịch trình',
      navigation: 'Điều hướng',
      trending: 'Xu hướng',
      library: 'Kho phim',
      switchModule: 'Chuyển ứng dụng',
      socialHomeTitle: 'Mạng xã hội',
      socialHomeSubtitle: 'Nhắn tin, kết bạn và quản lý hồ sơ cá nhân.',
      socialChatDesc: 'Trao đổi trực tiếp với bạn bè.',
      socialFriendsDesc: 'Kết nối với những người cùng đam mê.',
      socialProfileDesc: 'Cập nhật thông tin và tùy chọn cá nhân.',
      travelHomeTitle: 'Du lịch',
      travelHomeSubtitle: 'Lập lịch trình và xem thời tiết tại điểm đến.',
      travelItineraryDesc: 'Lập kế hoạch chi tiết cho chuyến đi.',
      travelWeatherDesc: 'Cập nhật thời tiết tại điểm đến.',
      explore: 'Khám phá',
      adminPage: 'Trang Quản trị',
      login: 'Đăng nhập',
      register: 'Đăng ký',
      logout: 'Đăng xuất',
      profile: 'Trang cá nhân',
      friends: 'Bạn bè',
      chat: 'Nhắn tin',
      itinerary: 'Lịch trình',
      weather: 'Thời tiết',
      admin: 'Quản trị',
      settings: 'Cài đặt',
    },
  },
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'vi',
    interpolation: {
      escapeValue: false,
    },
  });

export default i18n;
