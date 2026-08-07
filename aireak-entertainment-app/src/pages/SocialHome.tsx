import React from 'react';
import { Box } from '@mui/material';

import PostFeed from '../components/Social/PostFeed';

const SocialHome: React.FC = React.memo(() => (
  <Box
    className="flex justify-center"
    sx={{ alignItems: 'flex-start', maxWidth: 680, mx: 'auto' }}
  >
    <Box sx={{ width: '100%', minWidth: 0 }}>
      <PostFeed />
    </Box>
  </Box>
));

SocialHome.displayName = 'SocialHome';

export default SocialHome;
