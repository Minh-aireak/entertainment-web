import React from 'react';
import { Box } from '@mui/material';

import PostFeed from '../components/Social/PostFeed';
import SocialSidebar from '../components/Social/SocialSidebar';

const SocialHome: React.FC = React.memo(() => (
  <Box
    className="flex justify-center gap-6"
    sx={{ alignItems: 'flex-start', maxWidth: 1040, mx: 'auto' }}
  >
    <Box sx={{ width: '100%', maxWidth: 680, minWidth: 0 }}>
      <PostFeed />
    </Box>
    <Box sx={{ display: { xs: 'none', lg: 'block' }, width: 320, flexShrink: 0 }}>
      <SocialSidebar />
    </Box>
  </Box>
));

SocialHome.displayName = 'SocialHome';

export default SocialHome;
