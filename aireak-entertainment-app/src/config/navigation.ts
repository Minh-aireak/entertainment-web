import type { SvgIconComponent } from '@mui/icons-material';
import {
  Home,
  Chat,
  People,
  Person,
  CalendarMonth,
  Cloud,
  Groups,
  Movie,
  EventNote,
} from '@mui/icons-material';

export type AppModule = 'social' | 'movie' | 'schedule';

export interface ModuleConfig {
  id: AppModule;
  labelKey: string;
  icon: SvgIconComponent;
  basePath: string;
  defaultPath: string;
}

export interface NavItemConfig {
  textKey: string;
  path: string;
  icon: SvgIconComponent;
  /** Match exact path only (e.g. module home) */
  end?: boolean;
}

export const APP_MODULES: ModuleConfig[] = [
  {
    id: 'social',
    labelKey: 'moduleSocial',
    icon: Groups,
    basePath: '/social',
    defaultPath: '/social',
  },
  {
    id: 'movie',
    labelKey: 'moduleMovie',
    icon: Movie,
    basePath: '/movie',
    defaultPath: '/movie',
  },
  {
    id: 'schedule',
    labelKey: 'moduleSchedule',
    icon: EventNote,
    basePath: '/schedule',
    defaultPath: '/schedule',
  },
];

export const MODULE_NAV: Record<AppModule, NavItemConfig[]> = {
  social: [
    { textKey: 'home', path: '/social', icon: Home, end: true },
    { textKey: 'chat', path: '/social/chat', icon: Chat },
    { textKey: 'friends', path: '/social/friends', icon: People },
    { textKey: 'profile', path: '/social/profile', icon: Person },
  ],
  movie: [
    { textKey: 'home', path: '/movie', icon: Home, end: true },
    { textKey: 'trending', path: '/movie/trending', icon: Movie },
    { textKey: 'library', path: '/movie/library', icon: Movie },
  ],
  schedule: [
    { textKey: 'itinerary', path: '/schedule/itinerary', icon: CalendarMonth },
    { textKey: 'weather', path: '/schedule/weather', icon: Cloud },
  ],
};

export function getActiveModule(pathname: string): AppModule {
  if (pathname.startsWith('/movie')) return 'movie';
  if (pathname.startsWith('/schedule') || pathname.startsWith('/travel')) return 'schedule';
  return 'social';
}

export function isNavItemActive(pathname: string, item: NavItemConfig): boolean {
  if (item.end) {
    return pathname === item.path;
  }
  return pathname === item.path || pathname.startsWith(`${item.path}/`);
}
