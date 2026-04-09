package com.sistema_contabilidade.home.controller;

import com.sistema_contabilidade.home.dto.HomeDashboardResponse;
import com.sistema_contabilidade.home.service.HomeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeDashboardController {

  private final HomeDashboardService homeDashboardService;

  @GetMapping("/dashboard")
  public ResponseEntity<HomeDashboardResponse> getDashboard(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return ResponseEntity.ok(homeDashboardService.getDashboard(authentication, role));
  }
}
