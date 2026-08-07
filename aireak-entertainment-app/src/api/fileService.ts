import axiosInstance from './axiosInstance';
import type { ApiResponse, CompletedPart, FileInfo, InitPresignedUploadResult } from '../models';

const PART_UPLOAD_MAX_RETRIES = 3;
const PART_UPLOAD_CONCURRENCY = 4;

// PUT thẳng 1 part lên B2 bằng presigned URL - KHÔNG dùng axiosInstance vì nó gắn cookie/CSRF/Bearer
// token của app, những thứ B2 không cần và không nên nhận. Retry vì đây là network call trực tiếp tới
// 1 provider bên ngoài, không có backend đứng giữa để tự retry hộ như luồng cũ.
const uploadPartWithRetry = async (url: string, blob: Blob): Promise<string> => {
  let lastError: unknown;
  for (let attempt = 1; attempt <= PART_UPLOAD_MAX_RETRIES; attempt++) {
    try {
      const response = await fetch(url, { method: 'PUT', body: blob });
      if (!response.ok) {
        throw new Error(`Part upload failed with status ${response.status}`);
      }
      const eTag = response.headers.get('ETag') ?? response.headers.get('etag');
      if (!eTag) {
        throw new Error(
          'Missing ETag header in B2 response - kiểm tra CORS rule của bucket có expose "ETag" không'
        );
      }
      return eTag;
    } catch (err) {
      lastError = err;
      if (attempt < PART_UPLOAD_MAX_RETRIES) {
        await new Promise((resolve) => setTimeout(resolve, attempt * 1000)); // backoff tuyến tính: 1s, 2s
      }
    }
  }
  throw lastError;
};

// Upload toàn bộ part thẳng lên B2 song song (worker pool giới hạn PART_UPLOAD_CONCURRENCY), không đi
// qua backend. onProgress được gọi sau mỗi part hoàn tất để cập nhật progress bar.
const uploadPartsDirect = async (
  file: File,
  init: InitPresignedUploadResult,
  onProgress?: (completedParts: number, totalParts: number) => void
): Promise<CompletedPart[]> => {
  const { partSize, parts } = init;
  const results: CompletedPart[] = new Array(parts.length);
  let nextIndex = 0;
  let completed = 0;

  const worker = async () => {
    for (;;) {
      const currentIndex = nextIndex++;
      if (currentIndex >= parts.length) return;

      const part = parts[currentIndex];
      const start = (part.partNumber - 1) * partSize;
      const end = Math.min(file.size, start + partSize);
      const blob = file.slice(start, end);

      const eTag = await uploadPartWithRetry(part.url, blob);
      results[currentIndex] = { partNumber: part.partNumber, eTag };
      completed += 1;
      onProgress?.(completed, parts.length);
    }
  };

  const workerCount = Math.min(PART_UPLOAD_CONCURRENCY, parts.length) || 1;
  await Promise.all(Array.from({ length: workerCount }, worker));
  return results;
};

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

  // Upload trực tiếp lên B2 qua presigned URL (không relay qua backend). Dùng cho file lớn (video)
  // thay cho luồng chunked-upload cũ vốn bắt mỗi chunk đi qua 1 request đồng bộ tới file-service.
  uploadFileDirect: async (
    file: File,
    onProgress?: (percent: number) => void
  ): Promise<{ id: string; url: string; duration?: number }> => {
    const initRes = await axiosInstance.post<ApiResponse<InitPresignedUploadResult>>('/files/media/upload/init', {
      fileName: file.name,
      contentType: file.type,
      fileSize: file.size,
    });
    const init = initRes.data.result;

    const parts = await uploadPartsDirect(file, init, (completedParts, totalParts) => {
      onProgress?.(Math.round((completedParts / totalParts) * 90));
    });

    const completeRes = await axiosInstance.post<ApiResponse<FileInfo>>('/files/media/upload/complete', {
      uploadId: init.uploadId,
      parts,
    });

    onProgress?.(100);
    return {
      id: completeRes.data.result.id,
      url: completeRes.data.result.url,
      duration: completeRes.data.result.duration,
    };
  },

  getFileInfo: async (fileId: string) => {
    const response = await axiosInstance.get<ApiResponse<FileInfo>>(`/files/media/info/${fileId}`);
    return response.data;
  },
};
