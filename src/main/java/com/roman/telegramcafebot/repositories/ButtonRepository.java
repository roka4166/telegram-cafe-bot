package com.roman.telegramcafebot.repositories;

import com.roman.telegramcafebot.models.Button;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ButtonRepository extends JpaRepository<Button, Integer> {
    public List<Button> findAllByBelongsToMenu(String belongsToMenu);

    List<Button> findAllByBelongsToMenuStartingWith(String startingWith);

    public Button findButtonByNameStartingWith(String name);

    List<Button> findAllByCallbackDataStartingWith(String startingWith);

    List<Button> findAllByIdIsGreaterThan(Integer id);

    Button findButtonByCallbackData(String callbackData);
}
