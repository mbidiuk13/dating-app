package ua.kpi.datingapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входу застосунку.
 * Архітектура: модульний моноліт (ЛР2). Модулі — пакети user, profile,
 * invitation, compatibility; кожен містить власні сутності, репозиторії та сервіси.
 */
@SpringBootApplication
public class DatingAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatingAppApplication.class, args);
    }
}
