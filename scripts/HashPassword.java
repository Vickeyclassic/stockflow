import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Run in an interactive terminal; only the BCrypt hash is printed. */
class HashPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("Run in an interactive terminal");
        char[] password = console.readPassword("New password (12+ characters): ");
        try {
            var value = new String(password);
            if (value.length() < 12 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes");
            System.out.println(new BCryptPasswordEncoder().encode(value));
        } finally { Arrays.fill(password, '\0'); }
    }
}
