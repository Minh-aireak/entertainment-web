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
  Explore,
  TrendingUp,
  Bookmark,
  AdminPanelSettings,
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
import NotificationMenu from './NotificationMenu';
import AccountMenu from './AccountMenu';

const NAVBAR_HEIGHT = 64;
const SIDEBAR_EXPANDED_WIDTH = 300;
const SIDEBAR_COLLAPSED_WIDTH = 80;
const ACCENT_GREEN = '#00A84E';

// Single source of truth for the collapse/expand timing so every animated
// element (container width, item padding, text fade) moves in lockstep.
// Mismatched durations were the cause of the icons/text jittering mid-toggle.
const SIDEBAR_TRANSITION_MS = 240;
const SIDEBAR_EASING = 'ease-in-out';
const sidebarTransition = (...props: string[]) =>
  props.map((prop) => `${prop} ${SIDEBAR_TRANSITION_MS}ms ${SIDEBAR_EASING}`).join(', ');

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
] satisfies SidebarMenuItem[];

const entertainmentMenuItems = [
  { id: 'explore', label: 'Khám phá phim', icon: Explore, path: '/film' },
  { id: 'trending', label: 'Thịnh hành', icon: TrendingUp, path: '/film/trending' },
  { id: 'watchlist', label: 'Thư viện của tôi', icon: Bookmark, path: '/film/library', badgeKey: 'watchlist' },
] satisfies SidebarMenuItem[];

const adminMenuItems = [
  { id: 'admin', label: 'Quản trị hệ thống', icon: AdminPanelSettings, path: '/admin' },
] satisfies SidebarMenuItem[];

const listItemSx = (selected: boolean, isCollapsed: boolean, isLast?: boolean) => ({
  borderRadius: '16px',
  mb: isLast ? 0 : 0.75,
  mx: isCollapsed ? 'auto' : 2,
  width: isCollapsed ? 52 : 'auto',
  height: 52,
  py: 0,
  px: isCollapsed ? 0 : 1.75,
  display: 'flex',
  alignItems: 'center',
  justifyContent: isCollapsed ? 'center' : 'flex-start',
  overflow: 'hidden',
  // Only layout-affecting props that actually change are transitioned, all on
  // the shared SIDEBAR_TRANSITION_MS/EASING so this never desyncs from the
  // container's own width transition.
  transition: sidebarTransition('width', 'margin', 'padding', 'background-color', 'color'),
  bgcolor: selected ? ACCENT_GREEN : 'transparent',
  opacity: selected ? 1 : 0.9,
  color: selected ? '#fff' : 'rgba(255,255,255,0.8)',
  position: 'relative' as const,
  '& .MuiListItemIcon-root': {
    color: selected ? '#fff' : 'rgba(255,255,255,0.6)',
    minWidth: isCollapsed ? 0 : 44,
    flexShrink: 0,
    display: 'flex',
    justifyContent: 'center',
    marginRight: isCollapsed ? 0 : 1,
    transition: sidebarTransition('min-width', 'margin-right', 'color'),
  },
  '& .MuiListItemText-root': {
    margin: 0,
    // Fixed width + animated maxWidth (both numeric) instead of width:'auto',
    // which CSS cannot interpolate and was causing the text to snap instead
    // of fade, colliding visually with the icon while it was still moving.
    width: '100%',
    maxWidth: isCollapsed ? 0 : 180,
    opacity: isCollapsed ? 0 : 1,
    overflow: 'hidden',
    whiteSpace: 'nowrap',
    ml: isCollapsed ? 0 : 0.5,
    pointerEvents: isCollapsed ? 'none' : 'auto',
    transition: sidebarTransition('opacity', 'max-width', 'margin-left'),
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

const isMenuItemActive = (pathname: string, path: string) => {
  if (path === '/social' || path === '/film') return pathname === path;
  return pathname === path || pathname.startsWith(`${path}/`);
};

interface SidebarPanelProps {
  isMobile: boolean;
  isCollapsed: boolean;
  counts: SidebarCounts;
  pathname: string;
  isAdmin: boolean;
  onToggleCollapse: () => void;
  onNavigate: () => void;
}

// Memoized so the sidebar only re-renders when its own props actually change
// (collapse state, active route, badge counts) — not on every MainLayout
// re-render caused by unrelated state like theme toggling or the account menu.
const SidebarPanel = React.memo(function SidebarPanel({
  isMobile,
  isCollapsed: collapsedProp,
  counts,
  pathname,
  isAdmin,
  onToggleCollapse,
  onNavigate,
}: SidebarPanelProps) {
  const isCollapsed = !isMobile && collapsedProp;
  const sidebarWidth = isMobile ? SIDEBAR_EXPANDED_WIDTH : (isCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

  const renderMenuSection = (title: string, items: SidebarMenuItem[], badgeColor: 'error' | 'primary', sectionPy: number) => (
    <Box sx={{ px: isCollapsed ? 1 : 2, py: sectionPy, transition: sidebarTransition('padding') }}>
      <Typography sx={{
        px: isCollapsed ? 0 : 2,
        mb: isCollapsed ? 0 : 1.5,
        fontSize: '0.7rem',
        fontWeight: 700,
        color: 'rgba(255,255,255,0.5)',
        letterSpacing: '0.12em',
        textTransform: 'uppercase' as const,
        opacity: isCollapsed ? 0 : 1,
        maxHeight: isCollapsed ? 0 : 24,
        overflow: 'hidden',
        transition: sidebarTransition('opacity', 'max-height', 'margin-bottom'),
      }}>
        {title}
      </Typography>
      <List sx={{ px: 0, py: 0 }}>
        {items.map((item, idx) => {
          const selected = isMenuItemActive(pathname, item.path);
          const Icon = item.icon;
          const isLast = idx === items.length - 1;
          const badgeValue = item.badgeKey ? counts[item.badgeKey] : 0;
          return (
            <Tooltip key={item.id} title={isCollapsed ? item.label : ""} placement="right">
              <ListItem disablePadding sx={{ mb: isLast ? 0 : 0.5 }}>
                <ListItemButton
                  component={Link}
                  to={item.path}
                  selected={selected}
                  onClick={() => isMobile && onNavigate()}
                  sx={listItemSx(selected, isCollapsed, isLast)}
                >
                  <ListItemIcon>
                    <Badge badgeContent={badgeValue} color={badgeColor} max={99} invisible={!isCollapsed || badgeValue === 0}>
                      <Icon fontSize="medium" />
                    </Badge>
                  </ListItemIcon>
                  <ListItemText primary={item.label} />
                  {badgeValue > 0 && !isCollapsed && (
                    <Box sx={{
                      minWidth: 22,
                      height: 22,
                      px: 0.75,
                      borderRadius: 11,
                      bgcolor: badgeColor === 'error' ? 'error.main' : 'rgba(255,255,255,0.18)',
                      color: '#fff',
                      fontSize: '0.7rem',
                      fontWeight: 800,
                      display: 'grid',
                      placeItems: 'center',
                    }}>
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
  );

  return (
    <Box
      className="sidebar-container"
      sx={{
        width: sidebarWidth,
        flexShrink: 0,
        background: '#121212',
        borderRight: '1px solid rgba(255,255,255,0.06)',
        height: '100%',
        transition: sidebarTransition('width'),
        willChange: 'width',
        contain: 'layout style',
        position: 'relative',
        boxShadow: '8px 0 24px rgba(0,0,0,0.15)',
        zIndex: 10,
      }}
    >
      {/* Clips the collapsing text/icons during the width transition. Kept separate from the
          outer container so the toggle button below (which pokes out past the right edge) isn't
          clipped along with it — it used to be, leaving only a sliver of the button clickable. */}
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
        {/* Sidebar Header */}
        <Box sx={{ px: isCollapsed ? 1.5 : 3, py: 3, display: 'flex', alignItems: 'center', gap: 2, transition: sidebarTransition('padding') }}>
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
            width: '100%',
            maxWidth: isCollapsed ? 0 : 200,
            overflow: 'hidden',
            whiteSpace: 'nowrap',
            transition: sidebarTransition('opacity', 'max-width', 'margin-left'),
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

        <Divider sx={{ mx: isCollapsed ? 2 : 3, borderColor: 'rgba(255,255,255,0.06)', transition: sidebarTransition('margin') }} />

        {renderMenuSection('MẠNG XÃ HỘI', socialMenuItems, 'error', 2)}
        {renderMenuSection('GIẢI TRÍ', entertainmentMenuItems, 'primary', 1)}
        {isAdmin && renderMenuSection('QUẢN TRỊ', adminMenuItems, 'primary', 1)}

        <Box sx={{ flexGrow: 1 }} />
      </Box>

      {/* Sidebar Toggle Button on Right Edge — a full circle straddling the border, sized as a
          real touch target (40px) instead of the sliver it used to be. */}
      {!isMobile && (
        <Tooltip title={isCollapsed ? 'Mở rộng menu' : 'Thu gọn menu'} placement="right">
          <IconButton
            onClick={onToggleCollapse}
            aria-label={isCollapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
            sx={{
              position: 'absolute',
              right: 0,
              top: '50%',
              transform: 'translate(50%, -50%)',
              width: 40,
              height: 40,
              borderRadius: '50%',
              bgcolor: '#1E1E1E',
              border: '1px solid rgba(255,255,255,0.12)',
              color: 'rgba(255,255,255,0.75)',
              zIndex: 20,
              boxShadow: '0 2px 10px rgba(0,0,0,0.45)',
              transition: `${sidebarTransition('background-color', 'color', 'border-color', 'box-shadow')}, transform 150ms ${SIDEBAR_EASING}`,
              '&:hover': {
                bgcolor: ACCENT_GREEN,
                borderColor: ACCENT_GREEN,
                color: '#fff',
                boxShadow: '0 4px 16px rgba(0,168,78,0.45)',
                transform: 'translate(50%, -50%) scale(1.08)',
              },
              '&:active': {
                transform: 'translate(50%, -50%) scale(0.94)',
              },
            }}
          >
            {isCollapsed ? <ChevronRight fontSize="medium" /> : <ChevronLeft fontSize="medium" />}
          </IconButton>
        </Tooltip>
      )}
    </Box>
  );
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

  const isAdmin = user?.roles.some((role) => role.name === 'ADMIN') ?? false;

  const displayName = profileData?.displayName || user?.username || 'User';
  const avatar = profileData?.avatar || '';
  const email = profileData?.email || user?.email || 'user@aireak.com';

  const handleNotificationsMarkedRead = useCallback(() => {
    setSidebarCounts((currentCounts) => ({
      ...currentCounts,
      notifications: 0,
    }));
  }, []);

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

  const handleToggleSidebarCollapse = useCallback(() => {
    setIsSidebarCollapsed((prev) => !prev);
  }, []);

  const handleMobileNavigate = useCallback(() => {
    setMobileOpen(false);
  }, []);

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
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <NotificationMenu
                  unreadCount={sidebarCounts.notifications}
                  active={location.pathname.startsWith('/social/notifications')}
                  onMarkedAllRead={handleNotificationsMarkedRead}
                />
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
                <AccountMenu
                  displayName={displayName}
                  email={email}
                  avatar={avatar}
                  active={location.pathname.startsWith('/social/profile')}
                  onLogout={handleLogout}
                />
              </Box>
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
          <SidebarPanel
            isMobile={false}
            isCollapsed={isSidebarCollapsed}
            counts={sidebarCounts}
            pathname={location.pathname}
            isAdmin={isAdmin}
            onToggleCollapse={handleToggleSidebarCollapse}
            onNavigate={handleMobileNavigate}
          />
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
          <SidebarPanel
            isMobile={true}
            isCollapsed={isSidebarCollapsed}
            counts={sidebarCounts}
            pathname={location.pathname}
            isAdmin={isAdmin}
            onToggleCollapse={handleToggleSidebarCollapse}
            onNavigate={handleMobileNavigate}
          />
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
