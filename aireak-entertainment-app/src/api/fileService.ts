import axiosInstance from './axiosInstance';
import type { ApiResponse, FileInfo } from '../models';

export const fileService = {
  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return axiosInstance.post<ApiResponse<FileInfo>>('/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  
  getFileInfo: (fileId: string) => 
    axiosInstance.get<ApiResponse<FileInfo>>(`/file/info/${fileId}`),
};
