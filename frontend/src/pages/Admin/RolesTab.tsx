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

interface RoleFormState {
  name: string;
  description: string;
}

const EMPTY_FORM: RoleFormState = { name: '', description: '' };

const RolesTab: React.FC = () => {
  const confirm = useConfirmDialog();
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
      toast.error(error.response?.data?.message || 'Không thể tải dữ liệu vai trò');
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
      toast.error('Tên vai trò không được để trống');
      return;
    }
    setSaving(true);
    try {
      const response = editingName
        ? await identityService.updateRole(form)
        : await identityService.createRole(form);

      if (response.code === 1000) {
        toast.success(response.message || 'Lưu vai trò thành công');
        setDialogOpen(false);
        loadData();
      } else {
        toast.error(response.message || 'Lưu vai trò thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Lưu vai trò thất bại');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (role: RoleResponse) => {
    const confirmed = await confirm({
      title: 'Xóa vai trò',
      message: `Bạn có chắc muốn xóa vai trò "${role.name}"? Vai trò đang được gán cho người dùng sẽ không thể xóa.`,
    });
    if (!confirmed) return;

    try {
      const response = await identityService.deleteRole(role.name);
      if (response.code === 1000) {
        toast.success(response.message || 'Xóa vai trò thành công');
        setRoles((prev) => prev.filter((r) => r.name !== role.name));
      } else {
        toast.error(response.message || 'Xóa vai trò thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Xóa vai trò thất bại');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          Thêm vai trò
        </Button>
      </Box>

      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>Tên</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Mô tả</TableCell>
              <TableCell sx={{ fontWeight: 'bold', textAlign: 'right' }}>Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
              </TableRow>
            ) : roles.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}>Chưa có vai trò nào</TableCell>
              </TableRow>
            ) : (
              roles.map((role) => (
                <TableRow key={role.name} hover>
                  <TableCell sx={{ fontWeight: 'medium' }}>{role.name}</TableCell>
                  <TableCell>{role.description}</TableCell>
                  <TableCell align="right">
                    <IconButton color="primary" title="Chỉnh sửa" onClick={() => openEditDialog(role)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton color="error" title="Xóa" onClick={() => handleDelete(role)}>
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
        <DialogTitle>{editingName ? 'Chỉnh sửa vai trò' : 'Thêm vai trò mới'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Tên vai trò"
            value={form.name}
            disabled={!!editingName}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value.toUpperCase() }))}
            fullWidth
            required
            sx={{ mt: 1 }}
          />
          <TextField
            label="Mô tả"
            value={form.description}
            onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            fullWidth
            multiline
            minRows={2}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Hủy</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Đang lưu...' : 'Lưu'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(RolesTab);