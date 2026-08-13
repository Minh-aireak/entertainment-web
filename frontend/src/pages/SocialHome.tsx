import React, { useCallback, useEffect, useState } from 'react';
import { Box, Fab, Tab, Tabs, Zoom } from '@mui/material';
import { AutoAwesome, KeyboardArrowUp, Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

import PostFeed from '../components/Social/PostFeed';
import MyPostsFeed from '../components/Social/MyPostsFeed';

const SCROLL_TOP_THRESHOLD = 400;

const tabPillSx = {
  textTransform: 'none' as const,
  fontWeight: 700,
  fontSize: '0.875rem',
  minHeight: 42,
  borderRadius: 999,
  px: 2,
  py: 1,
  color: 'text.secondary',
  gap: 1,
  transition: 'all 0.2s ease',
  '&.Mui-selected': {
    color: '#fff',
    bgcolor: 'primary.main',
  },
};

const SocialHome: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const [showScrollTop, setShowScrollTop] = useState(false);
  const [activeTab, setActiveTab] = useState(0);

  useEffect(() => {
    const handleScroll = () => {
      setShowScrollTop(window.scrollY > SCROLL_TOP_THRESHOLD);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleScrollToTop = useCallback(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  return (
    <Box
      className="flex justify-center"
      sx={{ alignItems: 'flex-start', maxWidth: 680, mx: 'auto' }}
    >
      <Box sx={{ width: '100%', minWidth: 0 }}>
        <Box sx={{
          mb: 3, display: 'inline-flex', maxWidth: '100%', overflow: 'auto',
          p: 0.5, borderRadius: 999, bgcolor: 'action.hover',
        }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
            variant="scrollable"
            scrollButtons={false}
            slotProps={{ indicator: { style: { display: 'none' } } }}
            sx={{ minHeight: 'auto', '& .MuiTabs-flexContainer': { gap: 0.5 } }}
          >
            <Tab disableRipple icon={<AutoAwesome fontSize="small" />} iconPosition="start" sx={tabPillSx} label={t('discoverFeedTitle')} />
            <Tab disableRipple icon={<Person fontSize="small" />} iconPosition="start" sx={tabPillSx} label={t('myPostsTitle')} />
          </Tabs>
        </Box>

        {activeTab === 0 ? <PostFeed /> : <MyPostsFeed />}
      </Box>

      <Zoom in={showScrollTop}>
        <Fab
          size="medium"
          onClick={handleScrollToTop}
          aria-label={t('scrollToTop')}
          sx={{
            position: 'fixed',
            bottom: 32,
            right: 32,
            bgcolor: 'primary.main',
            color: '#fff',
            zIndex: (theme) => theme.zIndex.speedDial,
            '&:hover': { bgcolor: 'primary.dark' },
          }}
        >
          <KeyboardArrowUp />
        </Fab>
      </Zoom>
    </Box>
  );
});

SocialHome.displayName = 'SocialHome';

export default SocialHome;
