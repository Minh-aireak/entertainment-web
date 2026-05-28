import React, { useState } from 'react';
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
} from '@mui/material';
import {
  Menu as MenuIcon,
  AdminPanelSettings,
  Logout,
  Movie,
  MailOutlined as MailIcon,
  PersonOutlined as ProfileIcon,
  LightMode as ThemeIcon,
  Edit as EditIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
  Groups as SocialIcon,
  EventNote as ScheduleIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSelector, useDispatch } from 'react-redux';
import { type RootState, logout } from '../../store/index';
import {
  APP_MODULES,
  MODULE_NAV,
  getActiveModule,
  isNavItemActive,
} from '../../config/navigation';

const NAVBAR_HEIGHT = 64;
const SIDEBAR_EXPANDED_WIDTH = 280;
const SIDEBAR_COLLAPSED_WIDTH = 80;
const BG_NAV = '#121212';
const BG_SIDEBAR = '#121212';
const BG_PAGE = '#0F0F0F';
const ACCENT_ORANGE = '#F57C00';
const ACCENT_RED = '#FF5252';

interface MainLayoutProps {
  children: React.ReactNode;
}

const pillButtonSx = {
  borderRadius: '24px',
  px: 3,
  py: 0.8,
  minHeight: 38,
  fontWeight: 600,
  fontSize: '0.9rem',
  textTransform: 'none' as const,
  boxShadow: 'none',
  transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
  '&:hover': { 
    boxShadow: '0 4px 12px rgba(245, 124, 0, 0.2)',
    transform: 'translateY(-1px)',
  },
};

const navIconSx = (selected: boolean) => ({
  color: selected ? ACCENT_ORANGE : 'rgba(255, 255, 255, 0.7)',
  transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
  '&:hover': {
    color: '#fff',
    bgcolor: 'rgba(255, 255, 255, 0.08)',
  },
  mx: 1,
});

const listItemSx = (selected: boolean, isCollapsed: boolean) => ({
  borderRadius: '12px',
  mb: 0.75,
  mx: isCollapsed ? 1 : 1.5,
  py: 1.25,
  px: isCollapsed ? 0 : 2,
  justifyContent: isCollapsed ? 'center' : 'flex-start',
  transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
  bgcolor: selected ? 'rgba(245, 124, 0, 0.08)' : 'transparent',
  border: selected ? `1px solid rgba(245, 124, 0, 0.15)` : '1px solid transparent',
  '& .MuiListItemIcon-root': { 
    color: selected ? ACCENT_ORANGE : 'rgba(255, 255, 255, 0.5)',
    minWidth: isCollapsed ? 0 : 40,
  },
  '& .MuiListItemText-primary': { 
    color: selected ? ACCENT_ORANGE : 'rgba(255, 255, 255, 0.7)',
    fontWeight: selected ? 700 : 500,
    fontSize: '0.95rem',
    display: isCollapsed ? 'none' : 'block',
  },
  '&:hover': {
    bgcolor: selected ? 'rgba(245, 124, 0, 0.12)' : 'rgba(255, 255, 255, 0.05)',
    '& .MuiListItemIcon-root': { color: selected ? ACCENT_ORANGE : '#fff' },
    '& .MuiListItemText-primary': { color: selected ? ACCENT_ORANGE : '#fff' },
  },
});

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth);

  const [mobileOpen, setMobileOpen] = useState(false);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  
  const activeModule = getActiveModule(location.pathname);
  const moduleNavItems = MODULE_NAV[activeModule];
  const activeModuleConfig = APP_MODULES.find((m) => m.id === activeModule)!;

  const handleDrawerToggle = () => setMobileOpen(!mobileOpen);
  const toggleSidebar = () => setIsSidebarCollapsed(!isSidebarCollapsed);

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  const featureSidebar = (isMobile: boolean = false) => {
    const isCollapsed = !isMobile && isSidebarCollapsed;
    const sidebarWidth = isMobile ? SIDEBAR_EXPANDED_WIDTH : (isCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

    return (
      <Box
        sx={{
          width: sidebarWidth,
          flexShrink: 0,
          bgcolor: BG_SIDEBAR,
          borderRight: '1px solid rgba(255,255,255,0.08)',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
          overflow: 'hidden',
          position: 'relative',
        }}
      >
        {/* Sidebar Header */}
        <Box sx={{ px: isCollapsed ? 0 : 3, py: 4, textAlign: isCollapsed ? 'center' : 'left' }}>
          {!isCollapsed && (
            <Typography
              variant="overline"
              sx={{
                color: 'rgba(255,255,255,0.4)',
                letterSpacing: '0.15em',
                fontSize: '0.7rem',
                fontWeight: 700,
              }}
            >
              {t('navigation')}
            </Typography>
          )}
          <Typography
            sx={{
              color: '#fff',
              fontWeight: 800,
              fontSize: isCollapsed ? '0.8rem' : '1.25rem',
              mt: isCollapsed ? 0 : 0.5,
              letterSpacing: '-0.02em',
              whiteSpace: 'nowrap',
            }}
          >
            {isCollapsed ? activeModuleConfig.id.toUpperCase().charAt(0) : t(activeModuleConfig.labelKey)}
          </Typography>
        </Box>

        {/* Main Modules (Social, Movie, Schedule) */}
        <List sx={{ px: isCollapsed ? 1 : 1.5, py: 0 }}>
          {!isCollapsed && (
            <Box sx={{ px: 1.5, mb: 1.5 }}>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.3)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '1px' }}>
                Main Apps
              </Typography>
            </Box>
          )}
          {APP_MODULES.map((mod) => {
            const modSelected = activeModule === mod.id;
            const Icon = mod.icon;
            return (
              <Tooltip key={mod.id} title={isCollapsed ? t(mod.labelKey) : ""} placement="right">
                <ListItem disablePadding sx={{ mb: 0.5 }}>
                  <ListItemButton
                    onClick={() => navigate(mod.defaultPath)}
                    selected={modSelected}
                    sx={listItemSx(modSelected, isCollapsed)}
                  >
                    <ListItemIcon>
                      <Icon fontSize="medium" />
                    </ListItemIcon>
                    <ListItemText primary={t(mod.labelKey)} />
                  </ListItemButton>
                </ListItem>
              </Tooltip>
            );
          })}
        </List>

        <Divider sx={{ my: 3, mx: 2, borderColor: 'rgba(255,255,255,0.05)' }} />

        {/* Module Specific Nav */}
        <List sx={{ px: isCollapsed ? 1 : 1.5, py: 0, flex: 1 }}>
          {!isCollapsed && (
            <Box sx={{ px: 1.5, mb: 1.5 }}>
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.3)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '1px' }}>
                {t(activeModuleConfig.labelKey)}
              </Typography>
            </Box>
          )}
          {moduleNavItems.map((item) => {
            const Icon = item.icon;
            const selected = isNavItemActive(location.pathname, item);
            return (
              <Tooltip key={item.path} title={isCollapsed ? t(item.textKey) : ""} placement="right">
                <ListItem disablePadding sx={{ mb: 0.5 }}>
                  <ListItemButton
                    component={Link}
                    to={item.path}
                    selected={selected}
                    onClick={() => isMobile && setMobileOpen(false)}
                    sx={listItemSx(selected, isCollapsed)}
                  >
                    <ListItemIcon>
                      <Icon fontSize="medium" />
                    </ListItemIcon>
                    <ListItemText primary={t(item.textKey)} />
                  </ListItemButton>
                </ListItem>
              </Tooltip>
            );
          })}
        </List>

        {/* Sidebar Footer / Toggle */}
        {!isMobile && (
          <Box sx={{ p: 2, borderTop: '1px solid rgba(255,255,255,0.05)' }}>
            <IconButton 
              onClick={toggleSidebar}
              sx={{ 
                width: '100%', 
                borderRadius: '12px',
                color: 'rgba(255,255,255,0.5)',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.05)', color: '#fff' }
              }}
            >
              {isCollapsed ? <ChevronRightIcon /> : <ChevronLeftIcon />}
            </IconButton>
          </Box>
        )}

        {!isCollapsed && (
          <Box sx={{ p: 2 }}>
            <Box
              sx={{
                bgcolor: 'rgba(245, 124, 0, 0.1)',
                borderRadius: '16px',
                p: 2,
                border: '1px solid rgba(245, 124, 0, 0.2)',
              }}
            >
              <Typography sx={{ color: '#fff', fontWeight: 700, fontSize: '0.85rem' }}>
                Aireak Premium
              </Typography>
              <Button
                fullWidth
                size="small"
                variant="contained"
                sx={{
                  mt: 1.5,
                  bgcolor: ACCENT_ORANGE,
                  color: '#fff',
                  fontWeight: 700,
                  fontSize: '0.75rem',
                  '&:hover': { bgcolor: '#E65100' },
                }}
              >
                Upgrade
              </Button>
            </Box>
          </Box>
        )}
      </Box>
    );
  };

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: BG_PAGE, display: 'flex', flexDirection: 'column' }}>
      <AppBar
        position="sticky"
        elevation={0}
        sx={{
          bgcolor: BG_NAV,
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
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
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              textDecoration: 'none',
              flexShrink: 0,
            }}
          >
            <Movie sx={{ color: ACCENT_ORANGE, fontSize: { xs: 24, md: 28 } }} />
            <Typography
              noWrap
              sx={{
                color: '#ffffff',
                fontWeight: 800,
                letterSpacing: '0.05em',
                fontSize: { xs: '0.9rem', sm: '1.05rem' },
                display: { xs: 'none', sm: 'block' },
              }}
            >
              AIREAK
            </Typography>
          </Box>

          <Box sx={{ flex: 1, display: { xs: 'none', md: 'flex' }, justifyContent: 'center', alignItems: 'center' }}>
            <Tooltip title={t('moduleSocial')}>
              <IconButton component={Link} to="/social" sx={navIconSx(activeModule === 'social')}>
                <SocialIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('moduleMovie')}>
              <IconButton component={Link} to="/movie" sx={navIconSx(activeModule === 'movie')}>
                <Movie />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('moduleSchedule')}>
              <IconButton component={Link} to="/schedule" sx={navIconSx(activeModule === 'schedule')}>
                <ScheduleIcon />
              </IconButton>
            </Tooltip>
            <Divider orientation="vertical" flexItem sx={{ mx: 2, my: 1.5, borderColor: 'rgba(255,255,255,0.1)' }} />
            <Tooltip title={t('messages')}>
              <IconButton component={Link} to="/social/chat" sx={navIconSx(location.pathname.includes('/chat'))}>
                <MailIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('profile')}>
              <IconButton component={Link} to="/social/profile" sx={navIconSx(location.pathname.includes('/profile'))}>
                <ProfileIcon />
              </IconButton>
            </Tooltip>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {!isAuthenticated ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Button
                  variant="contained"
                  onClick={() => navigate('/login')}
                  sx={{
                    ...pillButtonSx,
                    bgcolor: ACCENT_ORANGE,
                    color: '#fff',
                    '&:hover': { bgcolor: '#E65100' },
                  }}
                >
                  {t('login') || 'Login'}
                </Button>
                <Button
                  onClick={() => navigate('/register')}
                  sx={{
                    color: 'rgba(255, 255, 255, 0.7)',
                    textTransform: 'none',
                    fontWeight: 600,
                    '&:hover': { color: '#fff' },
                  }}
                >
                  {t('register') || 'Register'}
                </Button>
              </Box>
            ) : (
              <>
                <IconButton sx={{ color: 'rgba(255,255,255,0.7)' }}>
                  <ThemeIcon fontSize="small" />
                </IconButton>
                <IconButton sx={{ color: 'rgba(255,255,255,0.7)' }}>
                  <EditIcon fontSize="small" />
                </IconButton>
                <Typography
                  sx={{
                    color: 'rgba(255,255,255,0.5)',
                    fontSize: '0.8rem',
                    cursor: 'pointer',
                    '&:hover': { color: '#fff' },
                    display: { xs: 'none', sm: 'block' },
                  }}
                >
                  无障碍
                </Typography>

                {user?.roles.some((r) => r.name === 'ADMIN') && (
                  <IconButton
                    onClick={() => navigate('/admin')}
                    sx={{ color: ACCENT_ORANGE }}
                    title={t('adminPage')}
                  >
                    <AdminPanelSettings />
                  </IconButton>
                )}

                <IconButton onClick={handleLogout} sx={{ color: ACCENT_RED }}>
                  <Logout fontSize="small" />
                </IconButton>
              </>
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
              bgcolor: BG_NAV,
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
            bgcolor: BG_PAGE,
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
