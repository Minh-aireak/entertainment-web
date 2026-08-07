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
  Avatar,
  Paper,
  CircularProgress,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  TablePagination,
} from '@mui/material';
import { Add, Edit, Delete } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import type { ApiResponse, PageResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import AvatarUploadField from './AvatarUploadField';

export interface PersonItem {
  id: string;
  name: string;
  avatarUrl: string;
  avatarFileId?: string;
}

interface PersonManagementTabProps {
  addLabel: string;
  editTitle: string;
  createTitle: string;
  fetchPage: (page: number, size: number) => Promise<ApiResponse<PageResponse<PersonItem>>>;
  create: (data: { name: string; avatarUrl: string; avatarFileId?: string }) => Promise<ApiResponse<PersonItem>>;
  update: (id: string, data: { name: string; avatarUrl: string; avatarFileId?: string }) => Promise<ApiResponse<PersonItem>>;
  remove: (id: string) => Promise<ApiResponse<void>>;
  deleteConfirmMessage: (name: string) => string;
}

const PersonManagementTab: React.FC<PersonManagementTabProps> = ({
  addLabel,
  editTitle,
  createTitle,
  fetchPage,
  create,
  update,
  remove,
  deleteConfirmMessage,
}) => {
  const confirm = useConfirmDialog();
  const [items, setItems] = useState<PersonItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1); // 1-indexed, matches film_service
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [avatarFileId, setAvatarFileId] = useState('');
  const [saving, setSaving] = useState(false);

  const loadItems = async () => {
    setLoading(true);
    try {
      const response = await fetchPage(page, rowsPerPage);
      if (response.code === 1000 && response.result) {
        setItems(response.result.data);
        setTotalElements(response.result.totalElement);
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tải dữ liệu');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, rowsPerPage]);

  const openCreateDialog = () => {
    setEditingId(null);
    setName('');
    setAvatarUrl('');
    setAvatarFileId('');
    setDialogOpen(true);
  };

  const openEditDialog = (item: PersonItem) => {
    setEditingId(item.id);
    setName(item.name);
    setAvatarUrl(item.avatarUrl);
    setAvatarFileId(item.avatarFileId || '');
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!name.trim()) {
      toast.error('Tên không được để trống');
      return;
    }
    setSaving(true);
    try {
      const response = editingId
        ? await update(editingId, { name, avatarUrl, avatarFileId })
        : await create({ name, avatarUrl, avatarFileId });

      if (response.code === 1000) {
        toast.success('Lưu thành công');
        setDialogOpen(false);
        loadItems();
      } else {
        toast.error(response.message || 'Lưu thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Lưu thất bại');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: PersonItem) => {
    const confirmed = await confirm({
      title: 'Xác nhận xóa',
      message: deleteConfirmMessage(item.name),
    });
    if (!confirmed) return;

    try {
      const response = await remove(item.id);
      if (response.code === 1000) {
        toast.success('Xóa thành công');
        setItems((prev) => prev.filter((i) => i.id !== item.id));
      } else {
        toast.error(response.message || 'Xóa thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Xóa thất bại');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          {addLabel}
        </Button>
      </Box>

      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 500 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>Ảnh</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Tên</TableCell>
              <TableCell sx={{ fontWeight: 'bold', textAlign: 'right' }}>Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
              </TableRow>
            ) : items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3} align="center" sx={{ py: 6 }}>Chưa có dữ liệu</TableCell>
              </TableRow>
            ) : (
              items.map((item) => (
                <TableRow key={item.id} hover>
                  <TableCell><Avatar src={item.avatarUrl || undefined} /></TableCell>
                  <TableCell sx={{ fontWeight: 'medium' }}>{item.name}</TableCell>
                  <TableCell align="right">
                    <IconButton color="primary" title="Chỉnh sửa" onClick={() => openEditDialog(item)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton color="error" title="Xóa" onClick={() => handleDelete(item)}>
                      <Delete fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={totalElements}
          page={page - 1}
          onPageChange={(_e, newPage) => setPage(newPage + 1)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(1);
          }}
          labelRowsPerPage="Số dòng/trang"
        />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingId ? editTitle : createTitle}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Tên"
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            required
            sx={{ mt: 1 }}
          />
          <AvatarUploadField
            label="URL ảnh đại diện"
            value={avatarUrl}
            onChange={setAvatarUrl}
            onFileIdChange={setAvatarFileId}
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

export default memo(PersonManagementTab);
