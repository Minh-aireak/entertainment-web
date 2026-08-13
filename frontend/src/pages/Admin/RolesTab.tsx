import React, { memo, useEffect, useState } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Paper,
  CircularProgress,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@mui/material';
import { Add, Edit, Delete } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../../api/identityService';
import type { RoleResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import { useTranslation } from 'react-i18next';

interface RoleFormState {
  name: string;
  description: string;
}

const EMPTY_FORM: RoleFormState = { name: '', description: '' };

const RolesTab: React.FC = () => {
  const confirm = useConfirmDialog();
  const { t } = useTranslation();
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [form, setForm] = useState<RoleFormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const rolesRes = await identityService.getRoles();
      if (rolesRes.code === 1000 && rolesRes.result) setRoles(rolesRes.result);
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('rolesLoadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openCreateDialog = () => {
    setEditingName(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  };

  const openEditDialog = (role: RoleResponse) => {
    setEditingName(role.name);
    setForm({
      name: role.name,
      description: role.description,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error(t('roleNameRequired'));
      return;
    }
    setSaving(true);
    try {
      const response = editingName
        ? await identityService.updateRole(form)
        : await identityService.createRole(form);

      if (response.code === 1000) {
        toast.success(response.message || t('roleSaved'));
        setDialogOpen(false);
        loadData();
      } else {
        toast.error(response.message || t('roleSaveFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('roleSaveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (role: RoleResponse) => {
    const confirmed = await confirm({
      title: t('deleteRole'),
      message: t('deleteRoleConfirm', { name: role.name }),
    });
    if (!confirmed) return;

    try {
      const response = await identityService.deleteRole(role.name);
      if (response.code === 1000) {
        toast.success(response.message || t('roleDeleted'));
        setRoles((prev) => prev.filter((r) => r.name !== role.name));
      } else {
        toast.error(response.message || t('roleDeleteFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('roleDeleteFailed'));
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          {t('addRole')}
        </Button>
      </Box>

      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('name')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>{t('description')}</TableCell>
              <TableCell sx={{ fontWeight: 'bold', textAlign: 'right' }}>{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
              </TableRow>
            ) : roles.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}>{t('noRoles')}</TableCell>
              </TableRow>
            ) : (
              roles.map((role) => (
                <TableRow key={role.name} hover>
                  <TableCell sx={{ fontWeight: 'medium' }}>{role.name}</TableCell>
                  <TableCell>{role.description}</TableCell>
                  <TableCell align="right">
                    <IconButton color="primary" title={t('editing')} onClick={() => openEditDialog(role)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton color="error" title={t('delete')} onClick={() => handleDelete(role)}>
                      <Delete fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingName ? t('editRole') : t('addNewRole')}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('roleName')}
            value={form.name}
            disabled={!!editingName}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value.toUpperCase() }))}
            fullWidth
            required
            sx={{ mt: 1 }}
          />
          <TextField
            label={t('description')}
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
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

export default memo(RolesTab);
