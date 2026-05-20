package dto.auth;

public record RegisterRequest(
        String email,
        String password,
        String firstName,
        String lastName,
        String personalIdNumber,
        String phoneNumber
) {}
