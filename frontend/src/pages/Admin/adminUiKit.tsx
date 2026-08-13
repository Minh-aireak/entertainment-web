import React from 'react';
import { Box, Pagination, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useTranslation } from 'react-i18next';
import type { SvgIconComponent } from '@mui/icons-material';

// Shared visual language for the admin dashboard: stat cards, pill tab bar,
// status/role badges and a compact pager. Kept in one place so every admin
// tab looks consistent without duplicating the same sx blocks everywhere.

export const getInitials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('') || '?';

export const formatCompactNumber = (value: number, locale: string) =>
  new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : 'en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value);

interface StatCardProps {
  icon: SvgIconComponent;
  value: React.ReactNode;
  label: string;
}

export const StatCard: React.FC<StatCardProps> = ({ icon: Icon, value, label }) => (
  <Box
    sx={{
      flex: '1 1 220px',
      minWidth: 200,
      borderRadius: '16px',
      p: 2.5,
      bgcolor: 'background.paper',
      border: '1px solid',
      borderColor: 'divider',
    }}
  >
    <Box
      sx={{
        width: 40,
        height: 40,
        borderRadius: '12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: alpha('#00A84E', 0.12),
        color: 'primary.main',
        mb: 2,
      }}
    >
      <Icon fontSize="small" />
    </Box>
    <Typography sx={{ fontSize: '1.75rem', fontWeight: 800, lineHeight: 1.2 }}>
      {value}
    </Typography>
    <Typography sx={{ fontSize: '0.85rem', color: 'text.secondary', mt: 0.5 }}>
      {label}
    </Typography>
  </Box>
);

type PillTone = 'success' | 'error' | 'warning' | 'default';

interface PillProps {
  label: string;
  tone?: PillTone;
  dot?: boolean;
}

// Small rounded badge. `dot` gives the "● Hoạt động" status look; without it,
// it reads as a plain outlined tag (used for role/type labels).
export const Pill: React.FC<PillProps> = ({ label, tone = 'default', dot = false }) => {
  const theme = useTheme();
  const toneColor =
    tone === 'success' ? theme.palette.success.main :
    tone === 'error' ? theme.palette.error.main :
    tone === 'warning' ? theme.palette.warning.main :
    theme.palette.text.secondary;

  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.75,
        px: 1.25,
        py: 0.4,
        borderRadius: '999px',
        fontSize: '0.75rem',
        fontWeight: 700,
        color: toneColor,
        bgcolor: alpha(toneColor, 0.12),
        border: '1px solid',
        borderColor: alpha(toneColor, 0.3),
        whiteSpace: 'nowrap',
        lineHeight: 1.6,
      }}
    >
      {dot && <Box component="span" sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: toneColor, flexShrink: 0 }} />}
      {label}
    </Box>
  );
};

interface AdminTab {
  key: string;
  label: string;
  icon: SvgIconComponent;
}

interface AdminTabBarProps {
  tabs: AdminTab[];
  value: string;
  onChange: (key: string) => void;
}

export const AdminTabBar: React.FC<AdminTabBarProps> = ({ tabs, value, onChange }) => (
  <Box
    sx={{
      display: 'flex',
      flexWrap: 'wrap',
      gap: 0.5,
      p: '6px',
      borderRadius: '999px',
      bgcolor: 'background.paper',
      border: '1px solid',
      borderColor: 'divider',
      width: 'fit-content',
      maxWidth: '100%',
    }}
  >
    {tabs.map((tab) => {
      const active = tab.key === value;
      const Icon = tab.icon;
      return (
        <Box
          key={tab.key}
          component="button"
          type="button"
          onClick={() => onChange(tab.key)}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.75,
            px: 2,
            py: 1,
            border: 'none',
            borderRadius: '999px',
            cursor: 'pointer',
            fontWeight: 700,
            fontSize: '0.875rem',
            fontFamily: 'inherit',
            whiteSpace: 'nowrap',
            transition: 'background-color 0.2s ease, color 0.2s ease',
            bgcolor: active ? 'primary.main' : 'transparent',
            color: active ? '#fff' : 'text.secondary',
            '&:hover': {
              bgcolor: active ? 'primary.main' : 'action.hover',
              color: active ? '#fff' : 'text.primary',
            },
          }}
        >
          <Icon fontSize="small" />
          {tab.label}
        </Box>
      );
    })}
  </Box>
);

interface AdminPaginationProps {
  page: number; // 1-indexed
  rowsPerPage: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  itemLabel: string;
}

export const AdminPagination: React.FC<AdminPaginationProps> = ({ page, rowsPerPage, totalElements, onPageChange, itemLabel }) => {
  const { t } = useTranslation();
  const totalPages = Math.max(1, Math.ceil(totalElements / rowsPerPage));
  const from = totalElements === 0 ? 0 : (page - 1) * rowsPerPage + 1;
  const to = Math.min(page * rowsPerPage, totalElements);

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 1.5,
        px: 2.5,
        py: 2,
        borderTop: '1px solid',
        borderColor: 'divider',
      }}
    >
      <Typography sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
        {t('paginationSummary', { from, to, total: totalElements, label: itemLabel })}
      </Typography>
      <Pagination
        page={page}
        count={totalPages}
        onChange={(_e, value) => onPageChange(value)}
        shape="rounded"
        color="primary"
        size="small"
      />
    </Box>
  );
};

export const adminTableContainerSx = {
  borderRadius: '16px',
  border: '1px solid',
  borderColor: 'divider',
  backgroundImage: 'none',
} as const;

export const adminHeaderCellSx = {
  fontWeight: 700,
  fontSize: '0.72rem',
  letterSpacing: '0.06em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  bgcolor: 'transparent',
};

export const adminInputSx = {
  '& .MuiOutlinedInput-root': { borderRadius: '10px' },
};

export const adminToolbarSx = {
  display: 'flex',
  gap: 1.5,
  alignItems: 'center',
  flexWrap: 'wrap' as const,
  mb: 2,
};
