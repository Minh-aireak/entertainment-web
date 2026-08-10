import type { Country, Genre } from '../models';

export const GENRE_VALUES: Genre[] = [
  'ACTION',
  'COMEDY',
  'DRAMA',
  'HORROR',
  'ROMANCE',
  'SCI_FI',
  'THRILLER',
  'DOCUMENTARY',
  'ANIMATION',
  'FANTASY',
];

export const COUNTRY_VALUES: Country[] = [
  'USA',
  'VIETNAM',
  'KOREA',
  'JAPAN',
  'CHINA',
  'FRANCE',
  'UK',
  'GERMANY',
  'INDIA',
  'THAILAND',
];

export const GENRE_LABELS_VI: Record<Genre, string> = {
  ACTION: 'Hành động',
  COMEDY: 'Hài hước',
  DRAMA: 'Tâm lý',
  HORROR: 'Kinh dị',
  ROMANCE: 'Lãng mạn',
  SCI_FI: 'Khoa học viễn tưởng',
  THRILLER: 'Giật gân',
  DOCUMENTARY: 'Tài liệu',
  ANIMATION: 'Hoạt hình',
  FANTASY: 'Viễn tưởng',
};

export const COUNTRY_LABELS_VI: Record<Country, string> = {
  USA: 'Mỹ',
  VIETNAM: 'Việt Nam',
  KOREA: 'Hàn Quốc',
  JAPAN: 'Nhật Bản',
  CHINA: 'Trung Quốc',
  FRANCE: 'Pháp',
  UK: 'Anh',
  GERMANY: 'Đức',
  INDIA: 'Ấn Độ',
  THAILAND: 'Thái Lan',
};
