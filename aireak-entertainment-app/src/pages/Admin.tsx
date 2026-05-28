import React, { useState } from 'react';
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
  TextField,
  InputAdornment,
  Paper,
} from '@mui/material';
import { Search, Block, CheckCircle, Edit, Delete } from '@mui/icons-material';
import { toast } from 'react-hot-toast';

interface User {
  id: string;
  username: string;
  email: string;
  roles: string[];
  status: 'ACTIVE' | 'BLOCKED';
}

const AdminPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  
  // Mock data
  const [users, setUsers] = useState<User[]>([
    { id: '1', username: 'admin', email: 'admin@travel.com', roles: ['ADMIN', 'USER'], status: 'ACTIVE' },
    { id: '2', username: 'nguyenvana', email: 'vana@gmail.com', roles: ['USER'], status: 'ACTIVE' },
    { id: '3', username: 'tranthib', email: 'thib@gmail.com', roles: ['USER'], status: 'ACTIVE' },
    { id: '4', username: 'baduser', email: 'bad@spam.com', roles: ['USER'], status: 'BLOCKED' },
  ]);

  const handleToggleStatus = (id: string) => {
    setUsers(users.map(u => {
      if (u.id === id) {
        const newStatus = u.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE';
        toast.success(`Đã ${newStatus === 'ACTIVE' ? 'mở khóa' : 'khóa'} người dùng ${u.username}`);
        return { ...u, status: newStatus as any };
      }
      return u;
    }));
  };

  const filteredUsers = users.filter(u => 
    u.username.toLowerCase().includes(searchQuery.toLowerCase()) || 
    u.email.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', mb: 4 }}>
        <TextField
          size="small"
          placeholder="Tìm kiếm người dùng..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ width: 300 }}
        />
      </Box>

      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold', color: 'rgba(255, 255, 255, 0.7)' }}>ID</TableCell>
              <TableCell sx={{ fontWeight: 'bold', color: 'rgba(255, 255, 255, 0.7)' }}>Người dùng</TableCell>
              <TableCell sx={{ fontWeight: 'bold', color: 'rgba(255, 255, 255, 0.7)' }}>Email</TableCell>
              <TableCell sx={{ fontWeight: 'bold', color: 'rgba(255, 255, 255, 0.7)' }}>Vai trò</TableCell>
              <TableCell sx={{ fontWeight: 'bold', color: 'rgba(255, 255, 255, 0.7)' }}>Trạng thái</TableCell>
              <TableCell sx={{ fontWeight: 'bold', textAlign: 'right', color: 'rgba(255, 255, 255, 0.7)' }}>Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredUsers.map((user) => (
              <TableRow key={user.id} hover>
                <TableCell sx={{ fontWeight: 'medium' }}>{user.id}</TableCell>
                <TableCell sx={{ fontWeight: 'medium' }}>{user.username}</TableCell>
                <TableCell sx={{ fontWeight: 'medium' }}>{user.email}</TableCell>
                <TableCell sx={{ fontWeight: 'medium' }}>
                  <Box sx={{ display: 'flex', gap: 0.5 }}>
                    {user.roles.map(role => (
                      <Chip key={role} label={role} size="small" variant="outlined" color={role === 'ADMIN' ? 'primary' : 'default'} />
                    ))}
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip 
                    label={user.status} 
                    size="small" 
                    color={user.status === 'ACTIVE' ? 'success' : 'error'} 
                  />
                </TableCell>
                <TableCell align="right">
                  <IconButton color="primary" title="Chỉnh sửa">
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton 
                    color={user.status === 'ACTIVE' ? 'error' : 'success'} 
                    title={user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                    onClick={() => handleToggleStatus(user.id)}
                  >
                    {user.status === 'ACTIVE' ? <Block fontSize="small" /> : <CheckCircle fontSize="small" />}
                  </IconButton>
                  <IconButton color="error" title="Xóa">
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default AdminPage;
