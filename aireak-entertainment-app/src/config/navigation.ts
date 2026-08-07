import type { SvgIconComponent } from '@mui/icons-material';
import {
  Home,
  Chat,
  People,
  Person,
  CalendarMonth,
  Groups,
  Movie,
  EventNote,
  Search,
} from '@mui/icons-material';

export type AppModule = 'social' | 'film' | 'schedule';

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
    id: 'film',
    labelKey: 'moduleMovie',
    icon: Movie,
    basePath: '/film',
    defaultPath: '/film',
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
  film: [
    { textKey: 'home', path: '/film', icon: Home, end: true },
    { textKey: 'searchFilms', path: '/film/search', icon: Search },
    { textKey: 'watchTogether', path: '/film/watch-together', icon: Groups },
    { textKey: 'library', path: '/film/library', icon: Movie },
  ],
  schedule: [
    { textKey: 'itinerary', path: '/schedule/itinerary', icon: CalendarMonth },
  ],
};

export function getActiveModule(pathname: string): AppModule {
  if (pathname.startsWith('/film')) return 'film';
  if (pathname.startsWith('/schedule') || pathname.startsWith('/travel')) return 'schedule';
  return 'social';
}

export function isNavItemActive(pathname: string, item: NavItemConfig): boolean {
  if (item.end) {
    return pathname === item.path;
  }
  return pathname === item.path || pathname.startsWith(`${item.path}/`);
}
