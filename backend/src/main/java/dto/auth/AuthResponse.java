package dto.auth;

public record AuthResponse(
        String token,
        String type
) {}
