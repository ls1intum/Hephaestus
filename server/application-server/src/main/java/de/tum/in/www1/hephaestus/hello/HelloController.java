package de.tum.in.www1.hephaestus.hello;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HelloController {

  private final HelloService helloService;

  public HelloController(HelloService helloService) {
    this.helloService = helloService;
  }

  /**
   * Retrieves all {@link Hello} entities.
   * 
   * @return A set of all Hello entities
   */
  @GetMapping
  public Set<Hello> getAllHellos() {
    return helloService.getAllHellos();
  }

  /**
   * Creates a new {@link Hello} entity with the current timestamp.
   * 
   * @return The created Hello entity
   */
  @PostMapping
  public Hello addHello() {
    return helloService.addHello();
  }
}