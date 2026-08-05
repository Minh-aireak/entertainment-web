import React, { useRef, useState } from 'react';
import { Avatar, Box, Button, CircularProgress, TextField } from '@mui/material';
import { CloudUpload } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { fileService } from '../../api/fileService';

interface AvatarUploadFieldProps {
  label: string;
  value: string;
  onChange: (url: string) => void;
}

const AvatarUploadField: React.FC<AvatarUploadFieldProps> = ({ label, value, onChange }) => {
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
      <TextField
        fullWidth
        size="small"
        label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
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
