package com.sistema_contabilidade.security.filter;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DatabaseRoutingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      if (isReaderEligible(request)) {
        DatabaseRoutingContext.allowReader();
      } else {
        DatabaseRoutingContext.forceWriter();
      }
      filterChain.doFilter(request, response);
    } finally {
      DatabaseRoutingContext.clear();
    }
  }

  private boolean isReaderEligible(HttpServletRequest request) {
    boolean readMethod =
        HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
    return readMethod
        && request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE) instanceof UUID;
  }
}
