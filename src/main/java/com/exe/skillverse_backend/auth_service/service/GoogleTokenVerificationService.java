package com.exe.skillverse_backend.auth_service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.util.Map;

public interface GoogleTokenVerificationService {

    Map<String, Object> getUserInfoFromAccessToken(String accessToken) throws Exception;

    GoogleIdToken.Payload verifyIdToken(String idTokenString) throws Exception;
}
