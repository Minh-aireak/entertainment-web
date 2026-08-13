import React, { useEffect, useState } from 'react';
import { Box, Dialog, DialogContent, DialogTitle, IconButton, Tooltip, Typography } from '@mui/material';
import { Close, Group, ManageAccounts, People, PlayCircleOutlined, ViewModule } from '@mui/icons-material';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import UsersTab from './UsersTab';
import FilmsTab from './FilmsTab';
import EpisodesTab from './EpisodesTab';
import PeopleTab from './PeopleTab';
import RolesTab from './RolesTab';
import { identityService } from '../../api/identityService';
import { filmService } from '../../api/filmService';
import { StatCard, AdminTabBar, formatCompactNumber } from './adminUiKit';

const TAB_KEYS = ['users', 'films', 'episodes', 'people'] as const;
type TabKey = typeof TAB_KEYS[number];

interface AdminStats {
  users: number;
  films: number;
  episodes: number;
  people: number;
}

const AdminPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const { t, i18n } = useTranslation();

  const paramTab = searchParams.get('tab');
  const initialTab: TabKey = (TAB_KEYS as readonly string[]).includes(paramTab ?? '') ? (paramTab as TabKey) : 'users';
  const [activeTab, setActiveTab] = useState<TabKey>(initialTab);
  const [visitedTabs, setVisitedTabs] = useState<Set<TabKey>>(() => new Set([initialTab]));
  const [rolesDialogOpen, setRolesDialogOpen] = useState(false);
  const [stats, setStats] = useState<AdminStats | null>(null);

  useEffect(() => {
    let cancelled = false;

    const loadStats = async () => {
      try {
        const [usersRes, filmsRes, episodesRes, actorsRes, directorsRes] = await Promise.all([
          identityService.getUsers(0, 1),
          filmService.getPageFilms(1, 1),
          filmService.getEpisodesPage({ page: 1, size: 1 }),
          filmService.getAllActors(1, 1),
          filmService.getAllDirectors(1, 1),
        ]);

        const usersTotal = usersRes.code === 1000 ? usersRes.result?.totalElement ?? 0 : 0;
        const filmsTotal = filmsRes.code === 1000 ? filmsRes.result?.totalElement ?? 0 : 0;
        const episodesTotal = episodesRes.code === 1000 ? episodesRes.result?.totalElement ?? 0 : 0;
        const actorsTotal = actorsRes.code === 1000 ? actorsRes.result?.totalElement ?? 0 : 0;
        const directorsTotal = directorsRes.code === 1000 ? directorsRes.result?.totalElement ?? 0 : 0;

        if (!cancelled) {
          setStats({ users: usersTotal, films: filmsTotal, episodes: episodesTotal, people: actorsTotal + directorsTotal });
        }
      } catch {
        // Stat cards are a decorative overview only — silently skip on failure.
      }
    };

    loadStats();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleChangeTab = (key: string) => {
    const tabKey = key as TabKey;
    setActiveTab(tabKey);
    setVisitedTabs((prev) => (prev.has(tabKey) ? prev : new Set(prev).add(tabKey)));
    const nextParams = new URLSearchParams(searchParams);
    if (tabKey === 'users') nextParams.delete('tab');
    else nextParams.set('tab', tabKey);
    setSearchParams(nextParams, { replace: true });
  };

  const statCards: { key: TabKey; icon: typeof People; label: string }[] = [
    { key: 'users', icon: People, label: t('totalUsersLabel') },
    { key: 'films', icon: PlayCircleOutlined, label: t('totalFilmsLabel') },
    { key: 'episodes', icon: ViewModule, label: t('totalEpisodesLabel') },
    { key: 'people', icon: Group, label: t('totalPeopleLabel') },
  ];

  const tabs = [
    { key: 'users', label: t('users'), icon: People },
    { key: 'films', label: t('films'), icon: PlayCircleOutlined },
    { key: 'episodes', label: t('episodes'), icon: ViewModule },
    { key: 'people', label: t('actorsAndDirectors'), icon: Group },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {t('adminPageTitle')}
        </Typography>
        <Tooltip title={t('manageRoles')}>
          <IconButton
            onClick={() => setRolesDialogOpen(true)}
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: '12px',
              color: 'text.secondary',
              '&:hover': { color: 'primary.main', borderColor: 'primary.main', bgcolor: 'rgba(0, 168, 78, 0.08)' },
            }}
          >
            <ManageAccounts fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 3 }}>
        {statCards.map((card) => (
          <StatCard
            key={card.key}
            icon={card.icon}
            label={card.label}
            value={stats ? formatCompactNumber(stats[card.key], i18n.language) : '—'}
          />
        ))}
      </Box>

      <Box sx={{ mb: 3 }}>
        <AdminTabBar tabs={tabs} value={activeTab} onChange={handleChangeTab} />
      </Box>

      {visitedTabs.has('users') && (
        <Box sx={{ display: activeTab === 'users' ? 'block' : 'none' }}>
          <UsersTab />
        </Box>
      )}
      {visitedTabs.has('films') && (
        <Box sx={{ display: activeTab === 'films' ? 'block' : 'none' }}>
          <FilmsTab active={activeTab === 'films'} />
        </Box>
      )}
      {visitedTabs.has('episodes') && (
        <Box sx={{ display: activeTab === 'episodes' ? 'block' : 'none' }}>
          <EpisodesTab />
        </Box>
      )}
      {visitedTabs.has('people') && (
        <Box sx={{ display: activeTab === 'people' ? 'block' : 'none' }}>
          <PeopleTab />
        </Box>
      )}

      <Dialog open={rolesDialogOpen} onClose={() => setRolesDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {t('manageRoles')}
          <IconButton size="small" onClick={() => setRolesDialogOpen(false)}>
            <Close fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <RolesTab />
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default AdminPage;
