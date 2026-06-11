package com.sistema_contabilidade.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class PlaywrightConfig {

  @Bean(destroyMethod = "close")
  @Lazy
  public Playwright playwright() {
    return Playwright.create();
  }

  @Bean(destroyMethod = "close")
  @Lazy
  public Browser browser(Playwright playwright) {
    return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }
}
