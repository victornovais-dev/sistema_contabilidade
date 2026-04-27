package com.sistema_contabilidade.auth.dto;

public record AuthenticatedLoginResult(JwtLoginResponse response, String sessionToken) {}
