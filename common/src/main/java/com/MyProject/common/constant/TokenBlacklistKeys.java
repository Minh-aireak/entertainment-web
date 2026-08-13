package com.MyProject.common.constant;

/**
 * Redis key layout shared between the token issuer (identity-service, blocking Redis client)
 * and every reader (api-gateway's reactive decoder, common-security's blocking decoder used by
 * all other microservices). Keep the prefixes identical on both sides - a drift here silently
 * breaks revocation instead of failing loudly.
 */
public final class TokenBlacklistKeys {
    private static final String INVALIDATED_TOKEN_PREFIX = "invalidated_token:";
    private static final String USER_TOKENS_VALID_AFTER_PREFIX = "token_valid_after:";

    private TokenBlacklistKeys() {
    }

    public static String invalidatedTokenKey(String jti) {
        return INVALIDATED_TOKEN_PREFIX + jti;
    }

    public static String userTokensValidAfterKey(String userId) {
        return USER_TOKENS_VALID_AFTER_PREFIX + userId;
    }
}
