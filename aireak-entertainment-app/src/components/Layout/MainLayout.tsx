import React, { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
  Button,
  Divider,
  Tooltip,
  Avatar,
  Badge,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Movie,
  LightMode as ThemeIcon,
  DarkMode as DarkModeIcon,
  ChevronRight,
  ChevronLeft,
  Home,
  Message,
  Group,
  Person,
  Explore,
  TrendingUp,
  Bookmark,
  Notifications,
  Logout as LogoutIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSelector, useDispatch } from 'react-redux';
import { type RootState, logout, toggleThemeMode } from '../../store/index';
import { identityService } from '../../api/identityService';
import { clearClientAuthState } from '../../api/axiosInstance';
import { chatService } from '../../api/chatService';
import { friendService } from '../../api/friendService';
import { notificationService } from '../../api/notificationService';
import { filmService } from '../../api/filmService';

const NAVBAR_HEIGHT = 64;
const SIDEBAR_EXPANDED_WIDTH = 300;
const SIDEBAR_COLLAPSED_WIDTH = 80;
const ACCENT_GREEN = '#00A84E';
const ACCENT_RED = '#FF5252';

interface MainLayoutProps {
  children: React.ReactNode;
}

const pillButtonSx = {
  borderRadius: '24px',
  px: 3,
  py: 0.8,
  minHeight: 38,
  fontWeight: 700,
  fontSize: '0.9rem',
  textTransform: 'none' as const,
  boxShadow: 'none',
  transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
  '&:hover': { 
    boxShadow: '0 4px 12px rgba(0, 168, 78, 0.2)',
    transform: 'translateY(-1px)',
  },
};

type SidebarBadgeKey = 'messages' | 'friendRequests' | 'notifications' | 'watchlist';

interface SidebarMenuItem {
  id: string;
  label: string;
  icon: typeof Home;
  path: string;
  badgeKey?: SidebarBadgeKey;
}

interface SidebarCounts {
  messages: number;
  friendRequests: number;
  notifications: number;
  watchlist: number;
}

const EMPTY_SIDEBAR_COUNTS: SidebarCounts = {
  messages: 0,
  friendRequests: 0,
  notifications: 0,
  watchlist: 0,
};

const socialMenuItems = [
  { id: 'home', label: 'Trang chủ', icon: Home, path: '/social' },
  { id: 'messages', label: 'Nhắn tin', icon: Message, path: '/social/chat', badgeKey: 'messages' },
  { id: 'friends', label: 'Bạn bè', icon: Group, path: '/social/friends', badgeKey: 'friendRequests' },
  { id: 'profile', label: 'Trang cá nhân', icon: Person, path: '/social/profile' },
] satisfies SidebarMenuItem[];

const entertainmentMenuItems = [
  { id: 'explore', label: 'Khám phá phim', icon: Explore, path: '/film' },
  { id: 'trending', label: 'Thịnh hành', icon: TrendingUp, path: '/film/trending' },
  { id: 'watchlist', label: 'Thư viện của tôi', icon: Bookmark, path: '/film/library', badgeKey: 'watchlist' },
] satisfies SidebarMenuItem[];

const onlineMenuItems = [
  { id: 'notifications', label: 'Thông báo', icon: Notifications, path: '/social/notifications', badgeKey: 'notifications' },
] satisfies SidebarMenuItem[];

const listItemSx = (selected: boolean, isCollapsed: boolean, isLast?: boolean) => ({
  borderRadius: isCollapsed ? '16px' : '16px',
  mb: isLast ? 0 : 0.75,
  mx: isCollapsed ? 'auto' : 2,
  width: isCollapsed ? 52 : 'auto',
  height: isCollapsed ? 52 : 52,
  py: 0,
  px: isCollapsed ? 0 : 1.75,
  display: 'flex',
  alignItems: 'center',
  justifyContent: isCollapsed ? 'center' : 'flex-start',
  transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
  bgcolor: selected ? ACCENT_GREEN : 'transparent',
  opacity: selected ? 1 : 0.9,
  color: selected ? '#fff' : 'rgba(255,255,255,0.8)',
  position: 'relative' as const,
  '& .MuiListItemIcon-root': { 
    color: selected ? '#fff' : 'rgba(255,255,255,0.6)',
    minWidth: isCollapsed ? 0 : 44,
    display: 'flex',
    justifyContent: 'center',
    marginRight: isCollapsed ? 0 : 1,
    transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
  },
  '& .MuiListItemText-root': {
    margin: 0,
    opacity: isCollapsed ? 0 : 1,
    width: isCollapsed ? 0 : 'auto',
    visibility: isCollapsed ? 'hidden' : 'visible',
    overflow: 'hidden',
    ml: isCollapsed ? 0 : 0.5,
    transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
  },
  '& .MuiListItemText-primary': { 
    color: 'inherit',
    fontWeight: selected ? 700 : 500,
    fontSize: '0.95rem',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  '&:hover': {
    bgcolor: selected ? '#008F41' : 'rgba(0, 168, 78, 0.12)',
    opacity: 1,
    '& .MuiListItemIcon-root': { color: selected ? '#fff' : ACCENT_GREEN },
  },
});

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth);
  const { profileData } = useSelector((state: RootState) => state.profile);
  const themeMode = useSelector((state: RootState) => state.ui.themeMode);

  const [mobileOpen, setMobileOpen] = useState(false);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [sidebarCounts, setSidebarCounts] = useState<SidebarCounts>(EMPTY_SIDEBAR_COUNTS);

  const refreshSidebarCounts = useCallback(async () => {
    if (!isAuthenticated) {
      setSidebarCounts(EMPTY_SIDEBAR_COUNTS);
      return;
    }

    const [messages, requests, notifications, watchlist] = await Promise.allSettled([
      chatService.getUnreadCount(),
      friendService.getMyFriendRequests(1, 1),
      notificationService.getUnreadCount(),
      filmService.getMyFollowedFilms(),
    ]);

    setSidebarCounts((current) => ({
      messages: messages.status === 'fulfilled' ? messages.value.result?.total ?? 0 : current.messages,
      friendRequests: requests.status === 'fulfilled' ? requests.value.result?.totalElement ?? 0 : current.friendRequests,
      notifications: notifications.status === 'fulfilled' ? notifications.value.result ?? 0 : current.notifications,
      watchlist: watchlist.status === 'fulfilled' ? watchlist.value.result?.length ?? 0 : current.watchlist,
    }));
  }, [isAuthenticated]);

  useEffect(() => {
    refreshSidebarCounts();
  }, [location.pathname, refreshSidebarCounts]);

  useEffect(() => {
    const handleRefresh = () => refreshSidebarCounts();
    window.addEventListener('focus', handleRefresh);
    window.addEventListener('sidebar-counts:refresh', handleRefresh);
    const intervalId = window.setInterval(handleRefresh, 60_000);

    return () => {
      window.removeEventListener('focus', handleRefresh);
      window.removeEventListener('sidebar-counts:refresh', handleRefresh);
      window.clearInterval(intervalId);
    };
  }, [refreshSidebarCounts]);

  const handleDrawerToggle = () => setMobileOpen(!mobileOpen);

  const handleLogout = async () => {
    try {
      await identityService.logout();
    } catch (error) {
      console.error('Failed to logout on server:', error);
    } finally {
      clearClientAuthState();
      dispatch(logout());
      navigate('/login');
    }
  };

  const featureSidebar = (isMobile: boolean = false) => {
    const isCollapsed = !isMobile && isSidebarCollapsed;
    const sidebarWidth = isMobile ? SIDEBAR_EXPANDED_WIDTH : (isCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

    const displayName = profileData?.displayName || user?.username || 'User';
    const avatar = profileData?.avatar || '';
    const initial = displayName.charAt(0).toUpperCase();

    // Check if a menu item is active
    const isMenuItemActive = (path: string) => {
      if (path === '/social' || path === '/film') return location.pathname === path;
      return location.pathname === path || location.pathname.startsWith(`${path}/`);
    };

    return (
      <Box
        className="sidebar-container"
        sx={{
          width: sidebarWidth,
          flexShrink: 0,
          background: '#121212',
          borderRight: '1px solid rgba(255,255,255,0.06)',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          transition: 'width 0.35s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.35s ease',
          overflow: 'hidden',
          position: 'relative',
          boxShadow: '8px 0 24px rgba(0,0,0,0.15)',
          zIndex: 10,
        }}
      >
        {/* Sidebar Header */}
        <Box sx={{ px: isCollapsed ? 1.5 : 3, py: 3, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box 
            sx={{ 
              width: 48, 
              height: 48, 
              borderRadius: '16px', 
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              background: 'linear-gradient(135deg, #00A84E 0%, #00c75c 100%)',
              boxShadow: '0 4px 14px rgba(0, 168, 78, 0.3)',
              flexShrink: 0
            }}
          >
            <Movie sx={{ color: '#fff', fontSize: 24 }} />
          </Box>
          <Box sx={{ 
            opacity: isCollapsed ? 0 : 1, 
            width: isCollapsed ? 0 : 'auto', 
            overflow: 'hidden',
            transition: 'all 0.3s ease',
            ml: isCollapsed ? 0 : 0.5
          }}>
            <Typography sx={{ fontWeight: 800, fontSize: '1.15rem', color: '#fff', letterSpacing: '-0.02em' }}>
              AIREAK
            </Typography>
            <Typography sx={{ fontSize: '0.75rem', color: 'rgba(255,255,255,0.5)', mt: 0.2 }}>
              Entertainment Hub
            </Typography>
          </Box>
        </Box>

        <Divider sx={{ mx: isCollapsed ? 2 : 3, borderColor: 'rgba(255,255,255,0.06)' }} />

        {/* MẠNG XÃ HỘI Section */}
        <Box sx={{ px: isCollapsed ? 1 : 2, py: 2 }}>
          <Typography sx={{ 
            px: isCollapsed ? 0 : 2, 
            mb: 1.5, 
            fontSize: '0.7rem', 
            fontWeight: 700, 
            color: 'rgba(255,255,255,0.5)', 
            letterSpacing: '0.12em', 
            textTransform: 'uppercase' as const,
            opacity: isCollapsed ? 0 : 1,
            height: isCollapsed ? 0 : 'auto',
            overflow: 'hidden',
            transition: 'all 0.3s ease'
          }}>
            MẠNG XÃ HỘI
          </Typography>
          <List sx={{ px: 0, py: 0 }}>
            {socialMenuItems.map((item, idx) => {
              const selected = isMenuItemActive(item.path);
              const Icon = item.icon;
              const isLast = idx === socialMenuItems.length - 1;
              const badgeValue = item.badgeKey ? sidebarCounts[item.badgeKey] : 0;
              return (
                <Tooltip key={item.id} title={isCollapsed ? item.label : ""} placement="right">
                  <ListItem disablePadding sx={{ mb: isLast ? 0 : 0.5 }}>
                    <ListItemButton
                      component={Link}
                      to={item.path}
                      selected={selected}
                      onClick={() => isMobile && setMobileOpen(false)}
                      sx={listItemSx(selected, isCollapsed, isLast)}
                    >
                      <ListItemIcon>
                        <Badge badgeContent={badgeValue} color="error" max={99} invisible={!isCollapsed || badgeValue === 0}>
                          <Icon fontSize="medium" />
                        </Badge>
                      </ListItemIcon>
                      <ListItemText primary={item.label} />
                      {badgeValue > 0 && !isCollapsed && (
                        <Box sx={{ minWidth: 22, height: 22, px: 0.75, borderRadius: 11, bgcolor: 'error.main', color: '#fff', fontSize: '0.7rem', fontWeight: 800, display: 'grid', placeItems: 'center' }}>
                          {badgeValue > 99 ? '99+' : badgeValue}
                        </Box>
                      )}
                    </ListItemButton>
                  </ListItem>
                </Tooltip>
              );
            })}
          </List>
        </Box>

        {/* GIẢI TRÍ Section */}
        <Box sx={{ px: isCollapsed ? 1 : 2, py: 1 }}>
          <Typography sx={{ 
            px: isCollapsed ? 0 : 2, 
            mb: 1.5, 
            fontSize: '0.7rem', 
            fontWeight: 700, 
            color: 'rgba(255,255,255,0.5)', 
            letterSpacing: '0.12em', 
            textTransform: 'uppercase' as const,
            opacity: isCollapsed ? 0 : 1,
            height: isCollapsed ? 0 : 'auto',
            overflow: 'hidden',
            transition: 'all 0.3s ease'
          }}>
            GIẢI TRÍ
          </Typography>
          <List sx={{ px: 0, py: 0 }}>
            {entertainmentMenuItems.map((item, idx) => {
              const selected = isMenuItemActive(item.path);
              const Icon = item.icon;
              const isLast = idx === entertainmentMenuItems.length - 1;
              const badgeValue = item.badgeKey ? sidebarCounts[item.badgeKey] : 0;
              return (
                <Tooltip key={item.id} title={isCollapsed ? item.label : ""} placement="right">
                  <ListItem disablePadding sx={{ mb: isLast ? 0 : 0.5 }}>
                    <ListItemButton
                      component={Link}
                      to={item.path}
                      selected={selected}
                      onClick={() => isMobile && setMobileOpen(false)}
                      sx={listItemSx(selected, isCollapsed, isLast)}
                    >
                      <ListItemIcon>
                        <Badge badgeContent={badgeValue} color="primary" max={99} invisible={!isCollapsed || badgeValue === 0}>
                          <Icon fontSize="medium" />
                        </Badge>
                      </ListItemIcon>
                      <ListItemText primary={item.label} />
                      {badgeValue > 0 && !isCollapsed && (
                        <Box sx={{ minWidth: 22, height: 22, px: 0.75, borderRadius: 11, bgcolor: 'rgba(255,255,255,0.18)', color: '#fff', fontSize: '0.7rem', fontWeight: 800, display: 'grid', placeItems: 'center' }}>
                          {badgeValue > 99 ? '99+' : badgeValue}
                        </Box>
                      )}
                    </ListItemButton>
                  </ListItem>
                </Tooltip>
              );
            })}
          </List>
        </Box>

        <Box sx={{ flexGrow: 1 }} />

        <Divider sx={{ mx: isCollapsed ? 2 : 3, borderColor: 'rgba(255,255,255,0.06)' }} />

        {/* Updates Section */}
        <Box sx={{ px: isCollapsed ? 1 : 2, py: 2 }}>
          <Typography sx={{ 
            px: isCollapsed ? 0 : 2, 
            mb: 1.5, 
            fontSize: '0.7rem', 
            fontWeight: 700, 
            color: 'rgba(255,255,255,0.5)', 
            letterSpacing: '0.12em', 
            textTransform: 'uppercase' as const,
            opacity: isCollapsed ? 0 : 1,
            height: isCollapsed ? 0 : 'auto',
            overflow: 'hidden',
            transition: 'all 0.3s ease'
          }}>
            CẬP NHẬT
          </Typography>
          <List sx={{ px: 0, py: 0 }}>
            {onlineMenuItems.map((item, idx) => {
              const selected = isMenuItemActive(item.path);
              const Icon = item.icon;
              const isLast = idx === onlineMenuItems.length - 1;
              const badgeValue = item.badgeKey ? sidebarCounts[item.badgeKey] : 0;
              return (
                <Tooltip key={item.id} title={isCollapsed ? item.label : ""} placement="right">
                  <ListItem disablePadding sx={{ mb: isLast ? 0 : 0.5 }}>
                    <ListItemButton
                      component={Link}
                      to={item.path}
                      selected={selected}
                      onClick={() => isMobile && setMobileOpen(false)}
                      sx={listItemSx(selected, isCollapsed, isLast)}
                    >
                      <ListItemIcon>
                        <Badge badgeContent={badgeValue} color="error" max={99} invisible={!isCollapsed || badgeValue === 0}>
                          <Icon fontSize="medium" />
                        </Badge>
                      </ListItemIcon>
                      <ListItemText primary={item.label} />
                      {badgeValue > 0 && !isCollapsed && (
                        <Box sx={{ minWidth: 22, height: 22, px: 0.75, borderRadius: 11, bgcolor: 'error.main', color: '#fff', fontSize: '0.7rem', fontWeight: 800, display: 'grid', placeItems: 'center' }}>
                          {badgeValue > 99 ? '99+' : badgeValue}
                        </Box>
                      )}
                    </ListItemButton>
                  </ListItem>
                </Tooltip>
              );
            })}
          </List>

          {/* User Profile & Logout */}
          {isAuthenticated && (
            <Box sx={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: isCollapsed ? 0 : 2, 
              mt: 1.5,
              px: isCollapsed ? 0 : 1.5,
              py: isCollapsed ? 0 : 1,
              borderRadius: '16px',
              border: '1px solid rgba(255,255,255,0.06)',
              transition: 'all 0.3s ease'
            }}>
              <Avatar 
                src={avatar}
                sx={{ 
                  width: isCollapsed ? 44 : 44, 
                  height: isCollapsed ? 44 : 44, 
                  flexShrink: 0,
                  borderRadius: '14px',
                  background: 'linear-gradient(135deg, #00A84E 0%, #006b31 100%)',
                  fontWeight: 700,
                  fontSize: '1.1rem',
                  color: '#fff',
                  position: 'relative',
                  '&::after': {
                    content: '""',
                    position: 'absolute',
                    bottom: 2,
                    right: 2,
                    width: 12,
                    height: 12,
                    bgcolor: '#4caf50',
                    borderRadius: '50%',
                    border: '2px solid #121212'
                  }
                }}
              >
                {initial}
              </Avatar>
              <Box sx={{ 
                flex: 1, 
                minWidth: 0, 
                opacity: isCollapsed ? 0 : 1, 
                width: isCollapsed ? 0 : 'auto', 
                overflow: 'hidden',
                transition: 'all 0.3s ease'
              }}>
                <Typography sx={{ fontSize: '0.95rem', fontWeight: 700, color: '#fff', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                  {displayName}
                </Typography>
                <Typography sx={{ fontSize: '0.78rem', color: 'rgba(255,255,255,0.5)', mt: 0.2 }}>
                  {profileData?.email || user?.email || 'user@aireak.com'}
                </Typography>
              </Box>
              {!isCollapsed && (
                <Tooltip title={t('logout') || "Đăng xuất"} placement="top">
                  <IconButton 
                    onClick={handleLogout} 
                    sx={{
                      width: 36,
                      height: 36,
                      borderRadius: '12px',
                      color: 'rgba(255,255,255,0.6)',
                      transition: 'all 0.25s ease',
                      '&:hover': { 
                        bgcolor: 'rgba(255, 82, 82, 0.15)',
                        color: ACCENT_RED,
                      },
                    }}
                  >
                    <LogoutIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          )}
        </Box>

        {/* Sidebar Toggle Button on Right Edge */}
        {!isMobile && (
          <IconButton
            onClick={() => setIsSidebarCollapsed(!isSidebarCollapsed)}
            sx={{
              position: 'absolute',
              right: 0,
              top: '50%',
              transform: 'translateY(-50%) translateX(50%)',
              width: 32,
              height: 56,
              borderRadius: '0 12px 12px 0',
              bgcolor: '#121212',
              borderRight: '1px solid rgba(255,255,255,0.06)',
              borderTop: '1px solid rgba(255,255,255,0.06)',
              borderBottom: '1px solid rgba(255,255,255,0.06)',
              color: 'rgba(255,255,255,0.6)',
              zIndex: 20,
              transition: 'all 0.25s ease',
              '&:hover': { 
                bgcolor: '#1e1e1e',
                color: '#fff',
              },
            }}
          >
            {isSidebarCollapsed ? <ChevronRight fontSize="small" /> : <ChevronLeft fontSize="small" />}
          </IconButton>
        )}
      </Box>
    );
  };

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', display: 'flex', flexDirection: 'column' }}>
      <AppBar
        position="sticky"
        elevation={0}
        sx={{
          bgcolor: 'background.paper',
          borderBottom: '1px solid',
          borderColor: 'divider',
          zIndex: (theme) => theme.zIndex.drawer + 1,
        }}
      >
        <Toolbar sx={{ minHeight: NAVBAR_HEIGHT, px: { xs: 2, md: 3 }, gap: 2 }}>
          <IconButton
            color="inherit"
            onClick={handleDrawerToggle}
            sx={{ display: { md: 'none' }, mr: 0.5 }}
          >
            <MenuIcon />
          </IconButton>

          <Box
            component={Link}
            to="/social"
            sx={{
              display: { xs: 'flex', md: 'none' },
              alignItems: 'center',
              gap: 1.5,
              textDecoration: 'none',
              flexShrink: 0,
            }}
          >
            <Movie sx={{ color: ACCENT_GREEN, fontSize: 26 }} />
            <Typography
              noWrap
              sx={{
                color: 'text.primary',
                fontWeight: 800,
                letterSpacing: '-0.02em',
                fontSize: '1.05rem',
              }}
            >
              AIREAK
            </Typography>
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {!isAuthenticated ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Button
                  variant="contained"
                  onClick={() => navigate('/login')}
                  sx={{
                    ...pillButtonSx,
                    bgcolor: ACCENT_GREEN,
                    color: '#fff',
                    '&:hover': { bgcolor: '#008F41' },
                  }}
                >
                  {t('login') || 'Login'}
                </Button>
                <Button
                  onClick={() => navigate('/register')}
                  sx={{
                    color: 'text.secondary',
                    textTransform: 'none',
                    fontWeight: 600,
                    '&:hover': { color: 'text.primary' },
                  }}
                >
                  {t('register') || 'Register'}
                </Button>
              </Box>
            ) : (
              <Tooltip title={themeMode === 'dark' ? t('lightMode') || "Chế độ sáng" : t('darkMode') || "Chế độ tối"}>
                <IconButton 
                  onClick={() => dispatch(toggleThemeMode())}
                  sx={{ 
                    color: 'text.secondary',
                    width: 44,
                    height: 44,
                    borderRadius: '12px',
                    '&:hover': { bgcolor: 'rgba(0,168,78,0.08)' }
                  }}
                >
                  {themeMode === 'dark' ? <ThemeIcon fontSize="medium" /> : <DarkModeIcon fontSize="medium" />}
                </IconButton>
              </Tooltip>
            )}
          </Box>
        </Toolbar>
      </AppBar>

      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Box
          component="nav"
          sx={{
            display: { xs: 'none', md: 'flex' },
            flexShrink: 0,
            position: 'sticky',
            top: NAVBAR_HEIGHT,
            height: `calc(100vh - ${NAVBAR_HEIGHT}px)`,
          }}
        >
          {featureSidebar(false)}
        </Box>

        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { md: 'none' },
            '& .MuiDrawer-paper': {
              width: SIDEBAR_EXPANDED_WIDTH,
              background: '#121212',
              borderRight: '1px solid rgba(255,255,255,0.06)'
            },
          }}
        >
          {featureSidebar(true)}
        </Drawer>

        <Box
          component="main"
          sx={{
            flex: 1,
            minWidth: 0,
            p: { xs: 2, md: 3 },
            bgcolor: 'background.default',
            minHeight: `calc(100vh - ${NAVBAR_HEIGHT}px)`,
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
};

export default MainLayout;
