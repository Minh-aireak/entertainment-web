import React, { useState } from 'react';
import { Box, Tabs, Tab, Typography } from '@mui/material';
import UsersTab from './UsersTab';
import RolesTab from './RolesTab';
import ActorsTab from './ActorsTab';
import DirectorsTab from './DirectorsTab';
import FilmsTab from './FilmsTab';
import EpisodesTab from './EpisodesTab';

const TABS = [
  { label: 'Người dùng', component: UsersTab },
  { label: 'Vai trò', component: RolesTab },
  { label: 'Phim', component: FilmsTab },
  { label: 'Tập phim', component: EpisodesTab },
  { label: 'Diễn viên', component: ActorsTab },
  { label: 'Đạo diễn', component: DirectorsTab },
];

const AdminPage: React.FC = () => {
  const [tabIndex, setTabIndex] = useState(0);
  const ActiveTab = TABS[tabIndex].component;

  return (
    <Box>
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
        Trang quản trị
      </Typography>

      <Tabs
        value={tabIndex}
        onChange={(_e, value) => setTabIndex(value)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
      >
        {TABS.map((tab) => (
          <Tab key={tab.label} label={tab.label} />
        ))}
      </Tabs>

      <ActiveTab />
    </Box>
  );
};

export default AdminPage;
