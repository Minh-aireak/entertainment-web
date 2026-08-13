import React, { memo, useEffect, useMemo, useState } from 'react';
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import { Add, Clear, Delete, Edit, Search } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { ActorResponse, DirectorResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import AvatarUploadField from './AvatarUploadField';
import {
  AdminPagination,
  Pill,
  adminHeaderCellSx,
  adminInputSx,
  adminTableContainerSx,
  adminToolbarSx,
  getInitials,
} from './adminUiKit';

type PersonRole = 'ACTOR' | 'DIRECTOR';

interface PersonRow {
  id: string;
  name: string;
  avatarUrl: string;
  avatarFileId?: string;
  role: PersonRole;
}

const ROWS_PER_PAGE = 10;

const PeopleTab: React.FC = () => {
  const confirm = useConfirmDialog();
  const { t } = useTranslation();

  const [items, setItems] = useState<PersonRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState<'ALL' | PersonRole>('ALL');
  const [page, setPage] = useState(1);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<PersonRow | null>(null);
  const [form, setForm] = useState({ name: '', avatarUrl: '', avatarFileId: '', role: 'ACTOR' as PersonRole });
  const [saving, setSaving] = useState(false);

  const loadItems = async () => {
    setLoading(true);
    try {
      const [actorsRes, directorsRes] = await Promise.all([
        filmService.getAllActors(1, 1000),
        filmService.getAllDirectors(1, 1000),
      ]);
      const actorRows: PersonRow[] = actorsRes.code === 1000 && actorsRes.result
        ? actorsRes.result.data.map((a: ActorResponse) => ({ ...a, role: 'ACTOR' as const }))
        : [];
      const directorRows: PersonRow[] = directorsRes.code === 1000 && directorsRes.result
        ? directorsRes.result.data.map((d: DirectorResponse) => ({ ...d, role: 'DIRECTOR' as const }))
        : [];
      setItems([...actorRows, ...directorRows]);
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('peopleLoadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filteredItems = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return items.filter((item) => {
      if (roleFilter !== 'ALL' && item.role !== roleFilter) return false;
      if (term && !item.name.toLowerCase().includes(term)) return false;
      return true;
    });
  }, [items, searchTerm, roleFilter]);

  useEffect(() => {
    setPage(1);
  }, [searchTerm, roleFilter]);

  const pagedItems = filteredItems.slice((page - 1) * ROWS_PER_PAGE, page * ROWS_PER_PAGE);

  const roleLabel = (role: PersonRole) => (role === 'ACTOR' ? t('actorRole') : t('directorRole'));

  const openCreateDialog = () => {
    setEditingItem(null);
    setForm({ name: '', avatarUrl: '', avatarFileId: '', role: roleFilter !== 'ALL' ? roleFilter : 'ACTOR' });
    setDialogOpen(true);
  };

  const openEditDialog = (item: PersonRow) => {
    setEditingItem(item);
    setForm({ name: item.name, avatarUrl: item.avatarUrl, avatarFileId: item.avatarFileId || '', role: item.role });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error(t('nameRequired'));
      return;
    }
    setSaving(true);
    try {
      const payload = { name: form.name, avatarUrl: form.avatarUrl, avatarFileId: form.avatarFileId };
      const response = editingItem
        ? form.role === 'ACTOR'
          ? await filmService.updateActor(editingItem.id, payload)
          : await filmService.updateDirector(editingItem.id, payload)
        : form.role === 'ACTOR'
          ? await filmService.createActor(payload)
          : await filmService.createDirector(payload);

      if (response.code === 1000) {
        toast.success(t('savedSuccessfully'));
        setDialogOpen(false);
        loadItems();
      } else {
        toast.error(response.message || t('saveFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: PersonRow) => {
    const confirmed = await confirm({
      title: t('deleteConfirmation'),
      message: item.role === 'ACTOR' ? t('deleteActorConfirm', { name: item.name }) : t('deleteDirectorConfirm', { name: item.name }),
    });
    if (!confirmed) return;

    try {
      const response = item.role === 'ACTOR' ? await filmService.deleteActor(item.id) : await filmService.deleteDirector(item.id);
      if (response.code === 1000) {
        toast.success(t('deletedSuccessfully'));
        setItems((prev) => prev.filter((i) => !(i.id === item.id && i.role === item.role)));
      } else {
        toast.error(response.message || t('deleteFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('deleteFailed'));
    }
  };

  return (
    <Box>
      <Box sx={adminToolbarSx}>
        <TextField
          size="small"
          placeholder={t('searchByName')}
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
        <FormControl size="small" sx={{ minWidth: 180, ...adminInputSx }}>
          <InputLabel id="role-filter-label">{t('roleFilterLabel')}</InputLabel>
          <Select
            labelId="role-filter-label"
            label={t('roleFilterLabel')}
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value as 'ALL' | PersonRole)}
          >
            <MenuItem value="ALL">{t('allRoles')}</MenuItem>
            <MenuItem value="ACTOR">{t('actorRole')}</MenuItem>
            <MenuItem value="DIRECTOR">{t('directorRole')}</MenuItem>
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog} sx={{ borderRadius: '10px' }}>
          {t('addPerson')}
        </Button>
      </Box>

      <TableContainer component={Paper} sx={adminTableContainerSx}>
        <Table sx={{ minWidth: 500 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={adminHeaderCellSx}>{t('fullName')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('roleFilterLabel')}</TableCell>
              <TableCell sx={{ ...adminHeaderCellSx, textAlign: 'right' }}>{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
              </TableRow>
            ) : pagedItems.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}>{t('noData')}</TableCell>
              </TableRow>
            ) : (
              pagedItems.map((item) => (
                <TableRow key={`${item.role}-${item.id}`} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar
                        src={item.avatarUrl || undefined}
                        sx={{ width: 40, height: 40, bgcolor: alpha('#00A84E', 0.15), color: 'primary.main', fontSize: '0.8rem', fontWeight: 700 }}
                      >
                        {getInitials(item.name)}
                      </Avatar>
                      <Box sx={{ fontWeight: 600 }}>{item.name}</Box>
                    </Box>
                  </TableCell>
                  <TableCell><Pill label={roleLabel(item.role)} /></TableCell>
                  <TableCell align="right">
                    <IconButton color="primary" title={t('editing')} onClick={() => openEditDialog(item)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton color="error" title={t('delete')} onClick={() => handleDelete(item)}>
                      <Delete fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <AdminPagination
          page={page}
          rowsPerPage={ROWS_PER_PAGE}
          totalElements={filteredItems.length}
          onPageChange={setPage}
          itemLabel={t('profilesLabel')}
        />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingItem ? (editingItem.role === 'ACTOR' ? t('editActor') : t('editDirector')) : t('addNewPerson')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <FormControl fullWidth required sx={{ mt: 1 }} disabled={!!editingItem}>
            <InputLabel id="person-role-label">{t('roleFilterLabel')}</InputLabel>
            <Select
              labelId="person-role-label"
              label={t('roleFilterLabel')}
              value={form.role}
              onChange={(e) => setForm((p) => ({ ...p, role: e.target.value as PersonRole }))}
            >
              <MenuItem value="ACTOR">{t('actorRole')}</MenuItem>
              <MenuItem value="DIRECTOR">{t('directorRole')}</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label={t('name')}
            value={form.name}
            onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
            fullWidth
            required
          />
          <AvatarUploadField
            label={t('avatarUrl')}
            value={form.avatarUrl}
            onChange={(url) => setForm((p) => ({ ...p, avatarUrl: url }))}
            onFileIdChange={(id) => setForm((p) => ({ ...p, avatarFileId: id }))}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? t('saving') : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(PeopleTab);
