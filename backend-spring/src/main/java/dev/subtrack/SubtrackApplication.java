package dev.subtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SubtrackApplication {

  public static void main(String[] args) {
    SpringApplication.run(SubtrackApplication.class, args);
  }
}
