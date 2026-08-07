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
  // Giữ lại các tab đã từng mở để tránh unmount/remount (và refetch API) khi quay lại tab cũ
  const [visitedTabs, setVisitedTabs] = useState<Set<number>>(() => new Set([0]));

  const handleChangeTab = (_e: React.SyntheticEvent, value: number) => {
    setTabIndex(value);
    setVisitedTabs((prev) => (prev.has(value) ? prev : new Set(prev).add(value)));
  };

  return (
    <Box>
      <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>
        Trang quản trị
      </Typography>

      <Tabs
        value={tabIndex}
        onChange={handleChangeTab}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
      >
        {TABS.map((tab) => (
          <Tab key={tab.label} label={tab.label} />
        ))}
      </Tabs>

      {TABS.map((tab, index) => {
        if (!visitedTabs.has(index)) return null;
        const TabComponent = tab.component;
        return (
          <Box key={tab.label} sx={{ display: index === tabIndex ? 'block' : 'none' }}>
            <TabComponent />
          </Box>
        );
      })}
    </Box>
  );
};

export default AdminPage;
