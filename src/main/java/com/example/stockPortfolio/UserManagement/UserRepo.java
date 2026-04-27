package com.example.stockPortfolio.UserManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

//we are using jpa to get some pre-defined methods, so we can access database through those
@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
