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
  Chip,
  Paper,
  CircularProgress,
  TablePagination,
} from '@mui/material';
import { Block, CheckCircle } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../../api/identityService';
import type { UserResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';

const UsersTab: React.FC = () => {
  const confirm = useConfirmDialog();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0); // 0-indexed, matches identity-service
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    let cancelled = false;

    const loadUsers = async () => {
      setLoading(true);
      try {
        const response = await identityService.getUsers(page, rowsPerPage);
        if (!cancelled && response.code === 1000 && response.result) {
          setUsers(response.result.data);
          setTotalElements(response.result.totalElement);
        }
      } catch (error: any) {
        if (!cancelled) toast.error(error.response?.data?.message || 'Không thể tải danh sách người dùng');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadUsers();
    return () => {
      cancelled = true;
    };
  }, [page, rowsPerPage]);

  const handleToggleStatus = async (targetUser: UserResponse) => {
    const isActive = targetUser.active;
    const confirmed = await confirm({
      title: isActive ? 'Khóa tài khoản' : 'Mở khóa tài khoản',
      message: `Bạn có chắc muốn ${isActive ? 'khóa' : 'mở khóa'} tài khoản "${targetUser.username}"?`,
      confirmColor: isActive ? 'error' : 'success',
      confirmText: isActive ? 'Khóa' : 'Mở khóa',
    });
    if (!confirmed) return;

    try {
      const response = await identityService.toggleAccount(targetUser.id);
      if (response.code === 1000) {
        toast.success(response.message || 'Cập nhật trạng thái thành công');
        setUsers((prev) => prev.map((u) => (u.id === targetUser.id ? { ...u, active: !u.active } : u)));
      } else {
        toast.error(response.message || 'Cập nhật trạng thái thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Cập nhật trạng thái thất bại');
    }
  };

  return (
    <Box>
      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>Username</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Email</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Vai trò</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Trạng thái</TableCell>
              <TableCell sx={{ fontWeight: 'bold', textAlign: 'right' }}>Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : users.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 6 }}>
                  Không có người dùng nào
                </TableCell>
              </TableRow>
            ) : (
              users.map((u) => (
                <TableRow key={u.id} hover>
                  <TableCell sx={{ fontWeight: 'medium' }}>{u.username}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {u.roles.map((role) => (
                        <Chip
                          key={role.name}
                          label={role.name}
                          size="small"
                          variant="outlined"
                          color={role.name === 'ADMIN' ? 'primary' : 'default'}
                        />
                      ))}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={u.active ? 'ACTIVE' : 'BLOCKED'}
                      size="small"
                      color={u.active ? 'success' : 'error'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <IconButton
                      color={u.active ? 'error' : 'success'}
                      title={u.active ? 'Khóa' : 'Mở khóa'}
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
        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          onPageChange={(_e, newPage) => setPage(newPage)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(0);
          }}
          labelRowsPerPage="Số dòng/trang"
        />
      </TableContainer>
    </Box>
  );
};

export default memo(UsersTab);
