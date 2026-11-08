package com.snovasys.ledgerline.web.dto;

import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.domain.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /** What the API ever shows about a user. The password hash is never part of any response. */
    public record UserView(UUID id, String email, String fullName, String phone, Role role,
                           UserStatus status, Instant createdAt) {

        public static UserView from(User user) {
            return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                    user.getRole(), user.getStatus(), user.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {
    }
}
