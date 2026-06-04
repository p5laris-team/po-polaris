package p5laris.character;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CharacterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CharacterApplication.class, args);
    }

}
