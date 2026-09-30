package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Dish;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID> {

  Optional<Dish> findByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
