import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import { type LatLon } from "../InterfaceDataType/DataType";
import { type DataWeatherResponse } from "../InterfaceDataType/DataType";

export const getDataWeather = async (
  dataPosition: LatLon
): Promise<DataWeatherResponse> => {
  return (await httpClient.post(API_ENDPOINTS.GET_DATA_WEATHER, dataPosition))
    .data.result as DataWeatherResponse;
};
