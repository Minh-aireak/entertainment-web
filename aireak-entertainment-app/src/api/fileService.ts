import axiosInstance from './axiosInstance';
import type { ApiResponse, FileInfo } from '../models';

export const fileService = {
  uploadFile: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axiosInstance.post<ApiResponse<FileInfo>>('/files/media/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
  
  initChunkedUpload: async () => {
    const response = await axiosInstance.post<ApiResponse<string>>('/files/media/upload/init');
    return response.data;
  },

  uploadChunk: async (uploadId: string, chunkIndex: number, file: Blob) => {
    const formData = new FormData();
    formData.append('uploadId', uploadId);
    formData.append('chunkIndex', chunkIndex.toString());
    formData.append('file', file);
    const response = await axiosInstance.post<ApiResponse<void>>('/files/media/upload/chunk', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  completeChunkedUpload: async (uploadId: string, fileName: string, contentType: string) => {
    const response = await axiosInstance.post<ApiResponse<FileInfo>>('/files/media/upload/complete', null, {
      params: { uploadId, fileName, contentType }
    });
    return response.data;
  },

  getFileInfo: async (fileId: string) => {
    const response = await axiosInstance.get<ApiResponse<FileInfo>>(`/files/media/info/${fileId}`);
    return response.data;
  },
};
