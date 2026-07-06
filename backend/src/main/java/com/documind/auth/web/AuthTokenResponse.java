package com.documind.auth.web;

public record AuthTokenResponse(String accessToken, String refreshToken) {
}
