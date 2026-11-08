package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.UserService;
import com.snovasys.ledgerline.web.dto.UserDtos.ChangePasswordRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UpdateProfileRequest;
import com.snovasys.ledgerline.web.dto.UserDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only "/me" endpoints exist: there is no way to pass another user's id here. */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserView me(@AuthenticationPrincipal AuthUser user) {
        return userService.getProfile(user);
    }

    @PutMapping
    public UserView update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user, request);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.noContent().build();
    }
}
