import React from 'react';

import PostFeed from '../components/Social/PostFeed';

const SocialHome: React.FC = React.memo(() => <PostFeed />);

SocialHome.displayName = 'SocialHome';

export default SocialHome;
