package de.tum.in.www1.hephaestus.hello;

import java.util.List;

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
   * Handles GET requests for retrieving all Hello entities.
   * 
   * @return a list of all Hello entities
   */
  @GetMapping
  public List<Hello> getAllHellos() {
    return helloService.getAllHellos();
  }

  /**
   * Handles POST requests for creating a new Hello entity.
   * 
   * @return the newly created Hello entity
   */
  @PostMapping
  public Hello addHello() {
    return helloService.addHello();
  }
}