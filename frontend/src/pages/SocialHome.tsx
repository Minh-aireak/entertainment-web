import React, { useCallback, useEffect, useState } from 'react';
import { Box, Fab, Tab, Tabs, Zoom } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { AutoAwesome, KeyboardArrowUp, Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

import PostFeed from '../components/Social/PostFeed';
import MyPostsFeed from '../components/Social/MyPostsFeed';
import { pillTabSx, pillTabsTrackSx } from '../styles/vibe';

const SCROLL_TOP_THRESHOLD = 400;

const SocialHome: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const theme = useTheme();
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
        <Box sx={{ ...pillTabsTrackSx(theme), mb: 3 }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
            variant="scrollable"
            scrollButtons={false}
            slotProps={{ indicator: { style: { display: 'none' } } }}
            sx={{ minHeight: 'auto', '& .MuiTabs-flexContainer': { gap: 0.5 } }}
          >
            <Tab disableRipple icon={<AutoAwesome fontSize="small" />} iconPosition="start" sx={pillTabSx(theme)} label={t('discoverFeedTitle')} />
            <Tab disableRipple icon={<Person fontSize="small" />} iconPosition="start" sx={pillTabSx(theme)} label={t('myPostsTitle')} />
          </Tabs>
        </Box>

        {activeTab === 0 ? <PostFeed /> : <MyPostsFeed />}
      </Box>

      <Zoom in={showScrollTop}>
        <Fab
          size="medium"
          color="primary"
          onClick={handleScrollToTop}
          aria-label={t('scrollToTop')}
          sx={{
            position: 'fixed',
            bottom: 32,
            right: 32,
            zIndex: theme.zIndex.speedDial,
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
