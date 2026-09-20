package com.stockflow.repository;
import com.stockflow.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByRole(User.Role role);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = :role order by u.id")
    java.util.List<User> lockByRole(@org.springframework.data.repository.query.Param("role") User.Role role);
}
