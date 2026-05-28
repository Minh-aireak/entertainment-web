package com.MyProject.profile.profile_service.repository.httpClient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.configuration.AuthenticationRequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "friend-service-client", url = "${app.services.friend.url}",
        configuration = { AuthenticationRequestInterceptor.class})
public interface FriendServiceClient {

    @GetMapping(value = "/friends/count")
    ApiResponse<Integer> countMyFriends();
}
