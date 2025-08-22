import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  UserProfileResponse,
  UpdateProfileResponse,
} from "../InterfaceDataType/DataTypeResponse";

export const getMyInfo = async (): Promise<UserProfileResponse> => {
  return (await httpClient.get(API_ENDPOINTS.MY_INFO))
    .data as UserProfileResponse;
};

export const updateProfile = async (
  formData: UserProfileResponse["result"]
): Promise<UpdateProfileResponse> => {
  return (await httpClient.post(API_ENDPOINTS.UPDATE_PROFILE, formData))
    .data as UpdateProfileResponse;
};

export const uploadAvatar = async (formData: FormData) => {
  return await httpClient.post(API_ENDPOINTS.UPDATE_AVATAR, formData);
};

// export const search = async (keyword: any) => {
//   return await httpClient.post(
//     API_ENDPOINTS.SEARCH_USER,
//     { keyword: keyword },
//     {
//       headers: {
//         Authorization: `Bearer ${AuthClientStore.getAccessToken}`,
//         "Content-Type": "application/json",
//       },
//     }
//   );
// };
