package com.saga.inventory;
import com.saga.inventory.entity.Inventory;
import com.saga.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class InventoryServiceApplication {
    public static void main(String[] args) { SpringApplication.run(InventoryServiceApplication.class, args); }

    @Bean
    CommandLineRunner seedData(InventoryRepository repo) {
        return args -> {
            repo.save(new Inventory("product-A", "Sản phẩm A", 10));
            repo.save(new Inventory("product-B", "Sản phẩm B", 5));
        };
    }
}
