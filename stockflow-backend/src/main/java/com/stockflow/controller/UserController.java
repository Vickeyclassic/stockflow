package com.stockflow.controller;

import com.stockflow.entity.User;
import com.stockflow.exception.DomainException;
import com.stockflow.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    public record Summary(Long id, String username, User.Role role, boolean active) {
        static Summary from(User user) {
            return new Summary(user.getId(), user.getUsername(), user.getRole(), user.isActive());
        }
    }
    public record Activation(@NotNull Boolean active) {}
    private final UserRepository users;
    public UserController(UserRepository users) { this.users = users; }

    @GetMapping
    public List<Summary> list() {
        return users.findAll(Sort.by("username")).stream().map(Summary::from).toList();
    }

    @PatchMapping("/{id}/active")
    @Transactional
    public Summary activate(@PathVariable Long id, @Valid @RequestBody Activation request,
                            @AuthenticationPrincipal Jwt principal) {
        // Serialize activation changes so concurrent administrators cannot disable each other.
        var administrators = users.lockByRole(User.Role.ADMIN);
        var user = users.findById(id).orElseThrow(() -> DomainException.notFound("User", id));
        if (!request.active() && user.getUsername().equals(principal.getSubject()))
            throw DomainException.conflict("SELF_DEACTIVATION", "You cannot deactivate your own account");
        if (!request.active() && user.isActive() && user.getRole() == User.Role.ADMIN
                && administrators.stream().filter(User::isActive).count() <= 1)
            throw DomainException.conflict("LAST_ADMIN", "At least one administrator must remain active");
        user.setActive(request.active());
        return Summary.from(users.saveAndFlush(user));
    }
}
