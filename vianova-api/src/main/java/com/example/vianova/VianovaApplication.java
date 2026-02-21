package com.example.vianova;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@SpringBootApplication
@RestController
public class VianovaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VianovaApplication.class, args);
    }

    @GetMapping("/hello")
    public ResponseEntity<String> sayHello(
            @RequestParam(value = "myName", defaultValue = "World") String name,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(String.format("Hello %s!", name));
    }
}
