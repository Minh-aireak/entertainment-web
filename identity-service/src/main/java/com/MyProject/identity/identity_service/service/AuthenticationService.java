package com.MyProject.identity.identity_service.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.MyProject.identity.identity_service.dto.event.UserRegisteredEvent;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.dto.response.OutboundUserResponse;
import com.MyProject.identity.identity_service.entity.RefreshToken;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.repository.RefreshTokenRepository;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.UserRepository;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    OutboundIdentityClient outboundIdentityClient;
    OutboundUserClient outboundUserClient;
    RoleRepository roleRepository;
    ResetPasswordRepository resetPasswordRepository;
    OutboxEventPublisher outboxEventPublisher;
    RefreshTokenRepository refreshTokenRepository;

    private String getInvalidatedTokenKey(String jid) {
        return "invalidated_token:" + jid;
    }

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    long validDuration;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    long refreshableDuration;

    @NonFinal
    @Value("${jwt.client-id}")
    String clientId;

    @NonFinal
    @Value("${jwt.client-secret}")
    String clientSecret;

    @NonFinal
    @Value("${jwt.redirect-uri}")
    String redirectUri;

    @NonFinal
    @Value("${jwt.grant-type}")
    String authorizationCode;

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse authentication(AuthenticationRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!user.isActive()) throw new AppException(ErrorCode.USER_NOT_ACTIVE);

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.PASSWORD_INCORRECT);

        String accessToken = generateToken(user);
        String refreshToken = generateRefreshToken(user);

        return AuthenticationResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public String generateRefreshToken(User user) {
        // Keep refresh tokens session-scoped so a login/refresh on one device does not
        // invalidate every other active device for the same user.
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plusSeconds(refreshableDuration))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return refreshToken.getToken();
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse refreshTokens(String refreshTokenStr) {
        // Find refresh token in DB
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        // Check if token is valid
        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Generate new access token
        User user = refreshToken.getUser();
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }
        String newAccessToken = generateToken(user);

        // Rotate only the token that was actually presented.
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        String newRefreshToken = generateRefreshToken(user);

        return AuthenticationResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeAllUserTokens(String userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeRefreshToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(String refreshTokenStr) {
        revokeRefreshToken(refreshTokenStr);
    }

    public IntrospectResponse introspectResponse(String token) {
        boolean checkValid = true;
        SignedJWT signedJWT = verifyToken(token, false);

        try {
            return IntrospectResponse.builder()
                    .valid(checkValid)
                    .userId(signedJWT.getJWTClaimsSet().getClaim("userId").toString())
                    .build();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.PARSE_EXCEPTION);
        }
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private boolean isTokenOwnedByUser(String token, String expectedUserId) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

            if (!signedJWT.verify(verifier)) {
                return false;
            }

            String actualUserId = signedJWT.getJWTClaimsSet().getClaim("userId").toString();

            return actualUserId.equals(expectedUserId);
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) {

        try {
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);
            Date expiryTime = (isRefresh)
                    ? new Date(signedJWT
                    .getJWTClaimsSet()
                    .getIssueTime()
                    .toInstant()
                    .plus(refreshableDuration, ChronoUnit.SECONDS).toEpochMilli())
                    : signedJWT.getJWTClaimsSet().getExpirationTime();

            boolean verified = signedJWT.verify(verifier);

            if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.TOKEN_INVALID);

            String jid = signedJWT.getJWTClaimsSet().getJWTID();
            
            // Skip Redis check as requested
            
            return signedJWT;
        } catch (JOSEException | ParseException e) {
            throw new AppException(ErrorCode.VERIFY_TOKEN_FAILED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse refreshToken(String refreshTokenStr) {
        return refreshTokens(refreshTokenStr);
    }

    public AuthenticationResponse outboundAuthenticate(String code) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("code", code);
        data.add("client_id", clientId);
        data.add("client_secret", clientSecret);
        data.add("redirect_uri", redirectUri);
        data.add("grant_type", authorizationCode);

        var response = outboundIdentityClient.exchangeToken(data);

        var userInfo = outboundUserClient.getInfo("json", response.getAccessToken());

        var user = findOrCreateUser(userInfo);

        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        var token = generateToken(user);
        var refreshToken = generateRefreshToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public User findOrCreateUser(OutboundUserResponse userInfo) {
        return userRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Role role = roleRepository.findById("USER").orElseThrow(()
                    -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

            String rawPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            User newUser = User.builder()
                    .username(userInfo.getEmail())
                    .email(userInfo.getEmail())
                    .password(passwordEncoder.encode(rawPassword))
                    .roles(Set.of(role))
                    .active(true)
                    .build();

            newUser = userRepository.save(newUser);

            // 1. Generate Reset Password Token
            ResetPassword resetPassword = ResetPassword.builder()
                    .token(UUID.randomUUID().toString())
                    .user(newUser)
                    .expiryDate(LocalDateTime.now().plusSeconds(3600))
                    .build();
            resetPasswordRepository.save(resetPassword);

            // Create ONE common event for both Profile and Notification services
            String baseUrl = redirectUri.substring(0, redirectUri.lastIndexOf("/"));
            String resetUrl = baseUrl + "/reset-password?token=" + resetPassword.getToken();

            UserRegisteredEvent userRegisteredEvent = UserRegisteredEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(newUser.getId())
                    .username(newUser.getUsername())
                    .email(newUser.getEmail())
                    .displayName(userInfo.getFamilyName() != null ? userInfo.getGivenName() + " " + userInfo.getFamilyName() : userInfo.getGivenName())
                    .firstName(userInfo.getGivenName())
                    .lastName(userInfo.getFamilyName())
                    .joinDate(LocalDateTime.now())
                    .generatedPassword(rawPassword)
                    .resetPasswordToken(resetPassword.getToken())
                    .resetPasswordUrl(resetUrl)
                    .build();

            outboxEventPublisher.publish(newUser.getId(), "user.registered", userRegisteredEvent);

            return newUser;
        });
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                stringJoiner.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions())) {
                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
                }
            });
        }
        return stringJoiner.toString();
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("aireak.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(validDuration, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .claim("userId", user.getId())
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
        } catch (Exception e) {
            throw new AppException(ErrorCode.SIGNER_EXCEPTION);
        }

        return jwsObject.serialize();
    }
}
