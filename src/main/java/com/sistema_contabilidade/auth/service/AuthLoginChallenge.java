package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;

public record AuthLoginChallenge(
    AuthProvider provider,
    String challengeName,
    String providerUsername,
    String challengeSession,
    String message) {}
