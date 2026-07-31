package com.sistema_contabilidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaContabilidadeApplication {

  public static void main(String[] args) {
    SpringApplication.run(SistemaContabilidadeApplication.class, args);
  }
}
