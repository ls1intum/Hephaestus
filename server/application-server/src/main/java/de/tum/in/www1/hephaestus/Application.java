package de.tum.in.www1.hephaestus;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
        System.out.println("Old TimeZone: " + TimeZone.getDefault().toString());
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
		System.out.println("New TimeZone: " + TimeZone.getDefault().toString());
		SpringApplication.run(Application.class, args);
	}
}
