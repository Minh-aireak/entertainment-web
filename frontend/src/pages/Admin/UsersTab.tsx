import React, { memo, useEffect, useMemo, useState } from 'react';
import {
  Avatar,
  Box,
  CircularProgress,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import { Block, CheckCircle, Clear, Search } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../../api/identityService';
import type { UserResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import { useTranslation } from 'react-i18next';
import {
  AdminPagination,
  Pill,
  adminHeaderCellSx,
  adminInputSx,
  adminTableContainerSx,
  adminToolbarSx,
  getInitials,
} from './adminUiKit';

const ROLE_LABEL_KEY: Record<string, string> = {
  ADMIN: 'roleAdmin',
  USER: 'roleUser',
};

const UsersTab: React.FC = () => {
  const confirm = useConfirmDialog();
  const { t } = useTranslation();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1); // 1-indexed locally, converted to 0-indexed for identity-service
  const [rowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'BLOCKED'>('ALL');

  useEffect(() => {
    let cancelled = false;

    const loadUsers = async () => {
      setLoading(true);
      try {
        const response = await identityService.getUsers(page - 1, rowsPerPage);
        if (!cancelled && response.code === 1000 && response.result) {
          setUsers(response.result.data);
          setTotalElements(response.result.totalElement);
        }
      } catch (error: any) {
        if (!cancelled) toast.error(error.response?.data?.message || t('usersLoadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadUsers();
    return () => {
      cancelled = true;
    };
  }, [page, rowsPerPage, t]);

  const filteredUsers = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return users.filter((u) => {
      if (term && !u.username.toLowerCase().includes(term) && !u.email.toLowerCase().includes(term)) return false;
      if (roleFilter !== 'ALL' && !u.roles.some((r) => r.name === roleFilter)) return false;
      if (statusFilter === 'ACTIVE' && !u.active) return false;
      if (statusFilter === 'BLOCKED' && u.active) return false;
      return true;
    });
  }, [users, searchTerm, roleFilter, statusFilter]);

  const handleToggleStatus = async (targetUser: UserResponse) => {
    const isActive = targetUser.active;
    const confirmed = await confirm({
      title: isActive ? t('lockAccount') : t('unlockAccount'),
      message: t('accountStatusConfirm', { action: isActive ? t('lock') : t('unlock'), username: targetUser.username }),
      confirmColor: isActive ? 'error' : 'success',
      confirmText: isActive ? t('lockAction') : t('unlockAction'),
    });
    if (!confirmed) return;

    try {
      const response = await identityService.toggleAccount(targetUser.id);
      if (response.code === 1000) {
        toast.success(response.message || t('statusUpdated'));
        setUsers((prev) => prev.map((u) => (u.id === targetUser.id ? { ...u, active: !u.active } : u)));
      } else {
        toast.error(response.message || t('statusUpdateFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('statusUpdateFailed'));
    }
  };

  return (
    <Box>
      <Box sx={adminToolbarSx}>
        <TextField
          size="small"
          placeholder={t('searchUsersPlaceholder')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ width: 280, ...adminInputSx }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: searchTerm ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setSearchTerm('')}>
                    <Clear fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
        />
        <FormControl size="small" sx={{ minWidth: 160, ...adminInputSx }}>
          <InputLabel id="role-filter-label">{t('filterByRole')}</InputLabel>
          <Select labelId="role-filter-label" label={t('filterByRole')} value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
            <MenuItem value="ALL">{t('allRoles')}</MenuItem>
            <MenuItem value="ADMIN">{t('roleAdmin')}</MenuItem>
            <MenuItem value="USER">{t('roleUser')}</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 160, ...adminInputSx }}>
          <InputLabel id="status-filter-label">{t('filterByStatus')}</InputLabel>
          <Select
            labelId="status-filter-label"
            label={t('filterByStatus')}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as 'ALL' | 'ACTIVE' | 'BLOCKED')}
          >
            <MenuItem value="ALL">{t('allStatuses')}</MenuItem>
            <MenuItem value="ACTIVE">{t('statusActive')}</MenuItem>
            <MenuItem value="BLOCKED">{t('statusLocked')}</MenuItem>
          </Select>
        </FormControl>
      </Box>

      <TableContainer component={Paper} sx={adminTableContainerSx}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={adminHeaderCellSx}>{t('users')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('email')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('role')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('status')}</TableCell>
              <TableCell sx={{ ...adminHeaderCellSx, textAlign: 'right' }}>{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : filteredUsers.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  {t('noUsers')}
                </TableCell>
              </TableRow>
            ) : (
              filteredUsers.map((u) => (
                <TableRow key={u.id} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar sx={{ width: 36, height: 36, bgcolor: alpha('#00A84E', 0.15), color: 'primary.main', fontSize: '0.8rem', fontWeight: 700 }}>
                        {getInitials(u.username)}
                      </Avatar>
                      <Box>
                        <Box sx={{ fontWeight: 600 }}>{u.username}</Box>
                        <Box sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>@{u.username}</Box>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {u.roles.map((role) => (
                        <Pill
                          key={role.name}
                          label={t(ROLE_LABEL_KEY[role.name] || role.name)}
                          tone={role.name === 'ADMIN' ? 'success' : 'default'}
                        />
                      ))}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Pill label={u.active ? t('statusActive') : t('statusLocked')} tone={u.active ? 'success' : 'error'} dot />
                  </TableCell>
                  <TableCell align="right">
                    <IconButton
                      color={u.active ? 'error' : 'success'}
                      title={u.active ? t('lockAction') : t('unlockAction')}
                      onClick={() => handleToggleStatus(u)}
                    >
                      {u.active ? <Block fontSize="small" /> : <CheckCircle fontSize="small" />}
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <AdminPagination page={page} rowsPerPage={rowsPerPage} totalElements={totalElements} onPageChange={setPage} itemLabel={t('users').toLowerCase()} />
      </TableContainer>
    </Box>
  );
};

export default memo(UsersTab);
