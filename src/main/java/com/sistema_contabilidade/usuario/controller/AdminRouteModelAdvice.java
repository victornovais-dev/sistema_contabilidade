package com.sistema_contabilidade.usuario.controller;

import com.sistema_contabilidade.security.service.AdminRouteService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class AdminRouteModelAdvice {

  private final AdminRouteService adminRouteService;

  @ModelAttribute("adminRouteConfig")
  public Map<String, String> adminRouteConfig() {
    return adminRouteService.routeConfig();
  }

  @ModelAttribute("adminPagePath")
  public String adminPagePath() {
    return adminRouteService.adminPagePath();
  }

  @ModelAttribute("manageRolesPath")
  public String manageRolesPath() {
    return adminRouteService.manageRolesPagePath();
  }

  @ModelAttribute("createUserPath")
  public String createUserPath() {
    return adminRouteService.createUserPagePath();
  }

  @ModelAttribute("updateUserPath")
  public String updateUserPath() {
    return adminRouteService.updateUserPagePath();
  }

  @ModelAttribute("adminApiBasePath")
  public String adminApiBasePath() {
    return adminRouteService.adminApiBasePath();
  }

  @ModelAttribute("adminUserApiBasePath")
  public String adminUserApiBasePath() {
    return adminRouteService.adminUserApiBasePath();
  }
}
