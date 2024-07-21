package de.tum.in.www1.hephaestus;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HelloController {

  @Autowired
  private HelloService helloService;

  @GetMapping
  public List<Hello> getAllHellos() {
    return helloService.getAllHellos();
  }

  @PostMapping
  public Hello addHello() {
    return helloService.addHello();
  }
}