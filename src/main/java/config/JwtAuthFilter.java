package config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import service.JwtService;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Pobierz nagłówek Authorization z każdego requestu
        String authHeader = request.getHeader("Authorization");

        // Jeśli brak nagłówka lub nie zaczyna się od "Bearer " — przepuść bez uwierzytelniania
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Odetnij "Bearer " i zostaw sam token
        String token = authHeader.substring(7);

        // Wyciągnij email z tokenu
        String email = jwtService.extractEmail(token);

        // Jeśli email istnieje i user nie jest jeszcze uwierzytelniony w tej sesji
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Pobierz usera z bazy przez UserDetailsService
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Sprawdź czy token jest ważny
            if (jwtService.isTokenValid(token, userDetails)) {

                // Utwórz obiekt uwierzytelnienia i wstaw go do kontekstu Spring Security
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Przekaż request do kolejnego filtra
        filterChain.doFilter(request, response);
    }
}
