package com.sistema_contabilidade.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@EnableConfigurationProperties({AuthProviderProperties.class, CognitoProperties.class})
public class AwsCognitoConfig {

  @Bean
  @ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
  CognitoIdentityProviderClient cognitoIdentityProviderClient(
      @org.springframework.beans.factory.annotation.Value("${aws.region}") String region) {
    return CognitoIdentityProviderClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
