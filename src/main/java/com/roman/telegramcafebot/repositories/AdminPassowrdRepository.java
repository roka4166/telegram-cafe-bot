package com.roman.telegramcafebot.repositories;
import com.roman.telegramcafebot.models.AdminPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminPassowrdRepository extends JpaRepository<AdminPassword, Integer> {
}
