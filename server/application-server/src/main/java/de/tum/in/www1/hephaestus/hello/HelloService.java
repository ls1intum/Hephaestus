package de.tum.in.www1.hephaestus.hello;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HelloService {

  private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

  private final HelloRepository helloRepository;

  public HelloService(HelloRepository helloRepository) {
    this.helloRepository = helloRepository;
  }

  /**
   * Retrieves all {@link Hello} entities from the repository.
   * 
   * @return A list of all Hello entities
   */
  public List<Hello> getAllHellos() {
    var hellos = helloRepository.findAll();
    logger.info("Getting Hellos: {}", hellos);
    return helloRepository.findAll();
  }

  /**
   * Creates a new {@link Hello} entity with the current timestamp and saves it to the repository.
   * 
   * @return The created Hello entity
   */
  public Hello addHello() {
    Hello hello = new Hello();
    hello.setTimestamp(Instant.now());
    logger.info("Adding new Hello with timestamp: {}", hello.getTimestamp());
    return helloRepository.save(hello);
  }
}