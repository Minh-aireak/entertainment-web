import { httpClient } from "../configurations/httpClient";
import { API_ENDPOINTS } from "../configurations/configuration";
import type {
  UserProfileResponse,
  UpdateProfileResponse,
} from "../InterfaceDataType/DataType";
import AuthClientStore from "../features/client-store/AuthClientStore";

export const getMyInfo = async (): Promise<UserProfileResponse> => {
  return (await httpClient.get(API_ENDPOINTS.MY_INFO))
    .data as UserProfileResponse;
};

export const updateProfile = async (
  formData: UserProfileResponse["result"]
): Promise<UpdateProfileResponse> => {
  return (await httpClient.put(API_ENDPOINTS.UPDATE_PROFILE, formData))
    .data as UpdateProfileResponse;
};

export const uploadAvatar = async (
  formData: FormData
): Promise<UserProfileResponse> => {
  return (
    await httpClient.put(API_ENDPOINTS.UPDATE_AVATAR, formData, {
      headers: {
        Authorization: `Bearer ${AuthClientStore.getAccessToken()}`,
        "Content-Type": "multipart/form-data",
      },
    })
  ).data as UserProfileResponse;
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
