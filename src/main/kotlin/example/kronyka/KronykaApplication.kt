package example.kronyka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KronykaApplication

fun main(args: Array<String>) {
    runApplication<KronykaApplication>(*args)
}
