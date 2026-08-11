export interface VideoMetadata {
  durationSeconds: number;
  durationMinutes: number;
  formattedDuration: string;
}

const formatDuration = (durationSeconds: number): string => {
  const hours = Math.floor(durationSeconds / 3600);
  const minutes = Math.floor((durationSeconds % 3600) / 60);
  const seconds = durationSeconds % 60;

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  return `${minutes}:${String(seconds).padStart(2, '0')}`;
};

export const readVideoMetadata = (file: File): Promise<VideoMetadata> =>
  new Promise((resolve, reject) => {
    const video = document.createElement('video');
    const objectUrl = URL.createObjectURL(file);

    const cleanup = () => {
      video.removeAttribute('src');
      video.load();
      URL.revokeObjectURL(objectUrl);
    };

    video.preload = 'metadata';
    video.onloadedmetadata = () => {
      const durationSeconds = Math.floor(video.duration);
      cleanup();

      if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) {
        reject(new Error('Không đọc được thời lượng của file video'));
        return;
      }

      resolve({
        durationSeconds,
        durationMinutes: Math.floor(durationSeconds / 60),
        formattedDuration: formatDuration(durationSeconds),
      });
    };
    video.onerror = () => {
      cleanup();
      reject(new Error('File đã chọn không phải video hợp lệ hoặc trình duyệt không hỗ trợ định dạng này'));
    };
    video.src = objectUrl;
  });
