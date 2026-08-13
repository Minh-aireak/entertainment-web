import axiosInstance from './axiosInstance';
import type {
  ApiResponse,
  FilmResponse,
  PageResponse,
  FilmSummaryResponse,
  FilmAggregateResponse,
  FilmDetailResponse,
  ActorResponse,
  DirectorResponse,
  ActorRequest,
  DirectorRequest,
  FilmRequest,
  RatingRequest,
  EpisodeResponse,
  EpisodeRequest,
  EpisodeSummaryResponse,
  FilmCategory,
  FilmSortField,
  SortDirection,
  Country,
  Genre,
  WatchProgressRequest,
  WatchProgressResponse
} from '../models';

const FILM_BASE_URL = '/films';
const FOLLOW_BASE_URL = `${FILM_BASE_URL}/follows`;
const EPISODE_BASE_URL = `${FILM_BASE_URL}/episodes`;
const WATCH_PROGRESS_BASE_URL = `${FILM_BASE_URL}/watch-progress`;

// Per-film debouncers map
const toggleFollowDebouncers = new Map<string, (...args: any[]) => Promise<any>>();
const rateFilmDebouncers = new Map<string, (...args: any[]) => Promise<any>>();

// Debounce helper
function createDebouncer<T extends (...args: any[]) => any>(func: T, wait: number) {
  let timeout: ReturnType<typeof setTimeout> | null = null;
  return (...args: Parameters<T>): Promise<ReturnType<T>> => {
    return new Promise((resolve) => {
      if (timeout) clearTimeout(timeout);
      timeout = setTimeout(async () => {
        resolve(await func(...args));
      }, wait);
    });
  };
}

export const filmService = {
  createActor: async (request: ActorRequest): Promise<ApiResponse<ActorResponse>> => {
    const response = await axiosInstance.post(`${FILM_BASE_URL}/actors`, request);
    return response.data;
  },

  getAllActors: async (page: number = 1, size: number = 20): Promise<ApiResponse<PageResponse<ActorResponse>>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/actors`, { params: { page, size } });
    return response.data;
  },

  getActor: async (id: string): Promise<ApiResponse<ActorResponse>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/actors/${id}`);
    return response.data;
  },

  updateActor: async (id: string, request: ActorRequest): Promise<ApiResponse<ActorResponse>> => {
    const response = await axiosInstance.put(`${FILM_BASE_URL}/actors/${id}`, request);
    return response.data;
  },

  deleteActor: async (id: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.delete(`${FILM_BASE_URL}/actors/${id}`);
    return response.data;
  },

  createDirector: async (request: DirectorRequest): Promise<ApiResponse<DirectorResponse>> => {
    const response = await axiosInstance.post(`${FILM_BASE_URL}/directors`, request);
    return response.data;
  },

  getAllDirectors: async (page: number = 1, size: number = 20): Promise<ApiResponse<PageResponse<DirectorResponse>>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/directors`, { params: { page, size } });
    return response.data;
  },

  getDirector: async (id: string): Promise<ApiResponse<DirectorResponse>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/directors/${id}`);
    return response.data;
  },

  updateDirector: async (id: string, request: DirectorRequest): Promise<ApiResponse<DirectorResponse>> => {
    const response = await axiosInstance.put(`${FILM_BASE_URL}/directors/${id}`, request);
    return response.data;
  },

  deleteDirector: async (id: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.delete(`${FILM_BASE_URL}/directors/${id}`);
    return response.data;
  },

  createFilm: async (request: FilmRequest): Promise<ApiResponse<FilmResponse>> => {
    const response = await axiosInstance.post(`${FILM_BASE_URL}/`, request);
    return response.data;
  },

  updateFilm: async (id: string, request: FilmRequest): Promise<ApiResponse<FilmResponse>> => {
    const response = await axiosInstance.put(`${FILM_BASE_URL}/${id}`, request);
    return response.data;
  },

  getPageFilms: async (page: number, size: number): Promise<ApiResponse<PageResponse<FilmSummaryResponse>>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/`, { params: { page, size } });
    return response.data;
  },

  getAggregateFilms: async (): Promise<ApiResponse<FilmAggregateResponse>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/aggregate`);
    return response.data;
  },

  getFilmAggregate: async (id: string): Promise<ApiResponse<FilmDetailResponse>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/${id}/aggregate`);
    return response.data;
  },

  searchFilms: async (title: string): Promise<ApiResponse<FilmSummaryResponse[]>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/search`, { params: { title } });
    return response.data;
  },

  getNowPlayingFilms: async (page: number = 1, size: number = 10): Promise<ApiResponse<PageResponse<FilmSummaryResponse>>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/now-playing`, { params: { page, size } });
    return response.data;
  },

  getTopRatedFilms: async (limit: number = 5): Promise<ApiResponse<FilmSummaryResponse[]>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/top-rated`, { params: { limit } });
    return response.data;
  },

  browseFilms: async (params: {
    category: FilmCategory;
    country?: Country;
    genre?: Genre;
    sortBy?: FilmSortField;
    sortDir?: SortDirection;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResponse<FilmSummaryResponse>>> => {
    const response = await axiosInstance.get(`${FILM_BASE_URL}/browse`, { params });
    return response.data;
  },

  rateFilm: (id: string, stars: number): Promise<ApiResponse<number>> => {
    if (!rateFilmDebouncers.has(id)) {
      rateFilmDebouncers.set(
        id,
        createDebouncer(async (filmId: string, ratingStars: number) => {
          const request: RatingRequest = { stars: ratingStars };
          const response = await axiosInstance.post(`${FILM_BASE_URL}/${filmId}/rating`, request);
          return response.data;
        }, 300)
      );
    }
    return rateFilmDebouncers.get(id)!(id, stars);
  },

  processFollowAction: (filmId: string, currentFollowed: boolean): Promise<ApiResponse<void>> => {
    const action = currentFollowed ? 'UNFOLLOW' : 'FOLLOW';
    if (!toggleFollowDebouncers.has(filmId)) {
      toggleFollowDebouncers.set(
        filmId,
        createDebouncer(async (id: string, followAction: string) => {
          const response = await axiosInstance.post(`${FOLLOW_BASE_URL}/${id}/${followAction}`);
          return response.data;
        }, 300)
      );
    }
    return toggleFollowDebouncers.get(filmId)!(filmId, action);
  },

  getMyFollowedFilms: async (): Promise<ApiResponse<FilmSummaryResponse[]>> => {
    const response = await axiosInstance.get(`${FOLLOW_BASE_URL}/my`);
    return response.data;
  },

  // Episode methods
  createEpisode: async (request: EpisodeRequest): Promise<ApiResponse<EpisodeResponse>> => {
    const response = await axiosInstance.post(EPISODE_BASE_URL, request);
    return response.data;
  },

  getEpisodesPage: async (params: {
    page: number;
    size: number;
    title?: string;
    filmId?: string;
  }): Promise<ApiResponse<PageResponse<EpisodeSummaryResponse>>> => {
    const response = await axiosInstance.get(EPISODE_BASE_URL, { params });
    return response.data;
  },

  getEpisodesByFilm: async (filmId: string): Promise<ApiResponse<EpisodeResponse[]>> => {
    const response = await axiosInstance.get(`${EPISODE_BASE_URL}/film/${filmId}`, {
      // Episode numbers can be edited while another tab is already open. Avoid reusing a
      // browser/proxy response so episode pickers always receive the latest database state.
      params: { _ts: Date.now() },
      headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    });
    return response.data;
  },

  updateEpisode: async (id: string, request: EpisodeRequest): Promise<ApiResponse<EpisodeResponse>> => {
    const response = await axiosInstance.put(`${EPISODE_BASE_URL}/${id}`, request);
    return response.data;
  },

  deleteEpisode: async (id: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.delete(`${EPISODE_BASE_URL}/${id}`);
    return response.data;
  },

  // Watch progress ("Continue Watching")
  upsertWatchProgress: async (request: WatchProgressRequest): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.put(WATCH_PROGRESS_BASE_URL, request);
    return response.data;
  },

  getMyContinueWatching: async (): Promise<ApiResponse<WatchProgressResponse[]>> => {
    const response = await axiosInstance.get(`${WATCH_PROGRESS_BASE_URL}/my`);
    return response.data;
  },

  getWatchProgress: async (filmId: string): Promise<ApiResponse<WatchProgressResponse | null>> => {
    const response = await axiosInstance.get(`${WATCH_PROGRESS_BASE_URL}/${filmId}`);
    return response.data;
  },

  removeWatchProgress: async (filmId: string): Promise<ApiResponse<void>> => {
    const response = await axiosInstance.delete(`${WATCH_PROGRESS_BASE_URL}/${filmId}`);
    return response.data;
  },
};
