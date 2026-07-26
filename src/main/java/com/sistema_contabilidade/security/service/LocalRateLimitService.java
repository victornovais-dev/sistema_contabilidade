package com.sistema_contabilidade.security.service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class LocalRateLimitService {

  private final int maxRequests;
  private final long windowMillis;
  private final Clock clock;
  private final Map<String, Deque<Long>> requestsByClient = new ConcurrentHashMap<>();

  @Autowired
  public LocalRateLimitService(
      @Value("${app.security.rate-limit.max-requests:120}") int maxRequests,
      @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
    this(maxRequests, windowSeconds, Clock.systemUTC());
  }

  LocalRateLimitService(int maxRequests, long windowSeconds, Clock clock) {
    if (maxRequests <= 0) {
      throw new IllegalArgumentException("Limite de requisicoes deve ser maior que zero");
    }
    if (windowSeconds <= 0) {
      throw new IllegalArgumentException("Janela do rate limit deve ser maior que zero");
    }
    this.maxRequests = maxRequests;
    this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public RateLimitDecision tryAcquire(String clientKey) {
    Objects.requireNonNull(clientKey, "clientKey");
    long now = clock.millis();
    Deque<Long> window = requestsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
    synchronized (window) {
      removeExpiredRequests(window, now);
      if (window.size() >= maxRequests) {
        return RateLimitDecision.REJECTED;
      }
      window.addLast(now);
      return RateLimitDecision.ALLOWED;
    }
  }

  private void removeExpiredRequests(Deque<Long> window, long now) {
    long cutoff = now - windowMillis;
    while (!window.isEmpty() && window.peekFirst() <= cutoff) {
      window.pollFirst();
    }
  }
}
