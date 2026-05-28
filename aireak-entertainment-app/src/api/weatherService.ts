import axiosInstance from './axiosInstance';
import type { ApiResponse, DataWeatherResponse, DataWeatherRequest } from '../models';

export const weatherService = {
  getWeather: (request: DataWeatherRequest) => 
    axiosInstance.post<ApiResponse<DataWeatherResponse>>('/post/get-data-weather', request),
};
