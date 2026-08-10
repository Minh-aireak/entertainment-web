import React, { useRef, useState } from 'react';
import { Avatar, Box, Button, CircularProgress, TextField, Typography } from '@mui/material';
import { CloudUpload } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { fileService } from '../../api/fileService';

interface AvatarUploadFieldProps {
  label: string;
  value: string;
  onChange: (url: string) => void;
  // File ID trong file-service (bucket B2 private) - bắn riêng khỏi onChange vì backend cần lưu ID
  // này để resolve lại presigned URL mỗi lần đọc, thay vì lưu thẳng URL tạm (hết hạn ~1h).
  onFileIdChange: (fileId: string) => void;
  // false: ẩn ô nhập URL tay, chỉ cho upload qua nút - dùng khi muốn bắt buộc ảnh phải qua file-service.
  allowManualUrl?: boolean;
}

const AvatarUploadField: React.FC<AvatarUploadFieldProps> = ({
  label,
  value,
  onChange,
  onFileIdChange,
  allowManualUrl = true,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      const response = await fileService.uploadFile(file);
      if (response.code === 1000 && response.result) {
        onChange(response.result.url);
        onFileIdChange(response.result.id);
      } else {
        toast.error(response.message || 'Tải ảnh thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Tải ảnh thất bại');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
      <Avatar src={value || undefined} sx={{ width: 56, height: 56 }} />
      {allowManualUrl ? (
        <TextField
          fullWidth
          size="small"
          label={label}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
            // Gõ tay nghĩa là dùng URL ngoài, không còn dùng ảnh đã upload nữa - bỏ fileId cũ đi.
            onFileIdChange('');
          }}
        />
      ) : (
        <Box sx={{ flex: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="caption" color={value ? 'success.main' : 'text.disabled'}>
            {value ? 'Đã có ảnh' : 'Chưa có ảnh'}
          </Typography>
        </Box>
      )}
      <Button
        variant="outlined"
        component="label"
        disabled={uploading}
        startIcon={uploading ? <CircularProgress size={16} /> : <CloudUpload />}
        sx={{ whiteSpace: 'nowrap' }}
      >
        Tải ảnh
        <input ref={inputRef} type="file" hidden accept="image/*" onChange={handleFileChange} />
      </Button>
    </Box>
  );
};

export default AvatarUploadField;
