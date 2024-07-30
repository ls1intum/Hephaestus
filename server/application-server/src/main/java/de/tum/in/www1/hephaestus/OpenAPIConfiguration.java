package de.tum.in.www1.hephaestus;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;

@Configuration
@OpenAPIDefinition(
  info = @Info(
    title = "Hephaestus API", 
    description = "API documentation for the Hephaestus application server.", 
    contact = @Contact(
      name = "Felix T.J. Dietrich",
      email = "felixtj.dietrich@tum.de"
    ), 
    license = @License(
      name = "MIT License",
      url = "https://github.com/ls1intum/Hephaestus/blob/develop/LICENSE"
    )
  ), 
  servers = {
    @Server(url = "/", description = "Default Server URL"),
  }
)
public class OpenAPIConfiguration {
}