package com.MyProject.identity.identity_service.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

import com.MyProject.event.dto.NotificationEvent;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;
import com.MyProject.identity.identity_service.repository.httpclient.UserProfileClient;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.entity.InvalidatedToken;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;

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
    InvalidatedTokenRepository invalidatedTokenRepository;
    OutboundIdentityClient outboundIdentityClient;
    OutboundUserClient outboundUserClient;
    UserProfileClient client;
    RoleRepository roleRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

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

    public AuthenticationResponse authentication(AuthenticationRequest request) throws JOSEException {
        var user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.PASSWORD_INCORRECT);

        String token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }

    protected String generateToken(User user) throws JOSEException {
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

        jwsObject.sign(new MACSigner(signerKey.getBytes()));

        return jwsObject.serialize();
    }

    protected String buildScope(User user) {
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

    @Transactional(rollbackFor = Exception.class)
    public void logout(LogoutRequest request, Jwt jwt) throws ParseException, JOSEException {
        if (!checkJidAndSignerKey(request.getToken(), jwt.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        SignedJWT signedToken = verifyToken(request.getToken(), true);

        String jid = signedToken.getJWTClaimsSet().getJWTID();
        Date expiryTime = signedToken.getJWTClaimsSet().getExpirationTime();

        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jid).expiryTime(expiryTime).build();

        invalidatedTokenRepository.save(invalidatedToken);
    }

    protected SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);
        Date expiryTime = (isRefresh)
                ? new Date(signedJWT
                        .getJWTClaimsSet()
                        .getIssueTime()
                        .toInstant()
                        .plus(refreshableDuration, ChronoUnit.SECONDS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        boolean verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.TOKEN_INVALID);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException((ErrorCode.TOKEN_ALREADY_INVALIDATED));

        return signedJWT;
    }

    public IntrospectResponse introspectResponse(IntrospectRequest request) {
        boolean checkValid = true;
        var token = request.getToken();

        try {
            var signedJWT = verifyToken(token, false);
        } catch (Exception exception) {
            checkValid = false;
        }

        return IntrospectResponse.builder().valid(checkValid).build();
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResponse refreshToken(RefreshRequest request, Jwt jwt) throws ParseException, JOSEException {
        if (!checkJidAndSignerKey(request.getToken(), jwt.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        SignedJWT signJWT = verifyToken(request.getToken(), true);

        var jid = signJWT.getJWTClaimsSet().getJWTID();
        var jet = signJWT.getJWTClaimsSet().getExpirationTime();

        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jid).expiryTime(jet).build();

        invalidatedTokenRepository.save(invalidatedToken);

        String username = signJWT.getJWTClaimsSet().getSubject();

        User user =
                userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var token = generateToken(user);

        return AuthenticationResponse.builder().token(token).build();
    }

    protected boolean checkJidAndSignerKey(String token, String expectedJid) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

            if(!signedJWT.verify(verifier)){
                return false;
            }

            String actualJid = signedJWT.getJWTClaimsSet().getJWTID();

            return actualJid.equals(expectedJid);
        } catch (WeakKeyException exception){
            throw new AppException(ErrorCode.WEAK_KEY);
        } catch (ParseException | JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public AuthenticationResponse outboundAuthenticate(String code) throws JOSEException {
        var response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest
                .builder()
                        .code(code)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .redirectUri(redirectUri)
                        .grantType(authorizationCode)
                .build());

        var userInfo = outboundUserClient.getInfo("json", response.getAccessToken());

        var user = userRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> {
            Role role = roleRepository.findById("USER").orElseThrow(()
                    -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

            User newUser = User.builder()
                    .username(userInfo.getEmail())
                    .email(userInfo.getEmail())
                    .password(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                    .roles(Set.of(role))
                    .build();

            UserProfileCreationRequest creationRequest = UserProfileCreationRequest.builder()
                    .username(userInfo.getEmail())
                    .email(userInfo.getEmail())
                    .fistName(userInfo.getFamilyName())
                    .lastName(userInfo.getGivenName())
                    .joinDate(LocalDateTime.now())
                    .build();

            client.createProfile(creationRequest);

            NotificationEvent notificationEvent = NotificationEvent.builder()
                    .channel("EMAIL")
                    .recipient(userInfo.getEmail())
                    .subject("Welcome to travelplanner!")
                    .body("Hello,\n" +
                            "You have successfully registered as a member of TravelPlanner with the following credentials:\n" +
                            "Username: " + userInfo.getEmail() +
                            "\n" +
                            "Password: " + newUser.getPassword() +
                            ".Please remember to change your password as soon as possible for your account security.")
                    .build();
            newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
            userRepository.save(newUser);
            kafkaTemplate.send("notification-delivery", notificationEvent);

            return newUser;
        });

        var token = generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }
}
