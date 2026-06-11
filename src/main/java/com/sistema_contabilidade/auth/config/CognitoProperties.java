package com.sistema_contabilidade.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cognito")
public class CognitoProperties {

  private String userPoolId;
  private String appClientId;
  private String appClientSecret;
  private String groupPrefix;
  private String usernameAttribute = "email";

  public String getUserPoolId() {
    return userPoolId;
  }

  public void setUserPoolId(String userPoolId) {
    this.userPoolId = userPoolId;
  }

  public String getAppClientId() {
    return appClientId;
  }

  public void setAppClientId(String appClientId) {
    this.appClientId = appClientId;
  }

  public String getAppClientSecret() {
    return appClientSecret;
  }

  public void setAppClientSecret(String appClientSecret) {
    this.appClientSecret = appClientSecret;
  }

  public String getGroupPrefix() {
    return groupPrefix;
  }

  public void setGroupPrefix(String groupPrefix) {
    this.groupPrefix = groupPrefix;
  }

  public String getUsernameAttribute() {
    return usernameAttribute;
  }

  public void setUsernameAttribute(String usernameAttribute) {
    this.usernameAttribute = usernameAttribute;
  }

  public boolean hasClientSecret() {
    return appClientSecret != null && !appClientSecret.isBlank();
  }
}
