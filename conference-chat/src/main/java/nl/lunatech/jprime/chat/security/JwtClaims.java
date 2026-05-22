package nl.lunatech.jprime.chat.security;

import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.ArrayList;
import java.util.List;

public final class JwtClaims {

    private JwtClaims() {}

    public static String string(JsonWebToken jwt, String name, String defaultValue) {
        if (jwt == null) return defaultValue;
        return jwt.<Object>claim(name)
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElse(defaultValue);
    }

    public static String string(JsonWebToken jwt, String name) {
        return string(jwt, name, null);
    }

    public static List<String> stringList(JsonWebToken jwt, String name) {
        if (jwt == null) return List.of();
        Object raw = jwt.<Object>claim(name).orElse(null);
        if (raw == null) return List.of();
        if (raw instanceof Iterable<?> it) {
            List<String> out = new ArrayList<>();
            for (Object o : it) out.add(String.valueOf(o));
            return out;
        }
        return List.of(String.valueOf(raw));
    }

    public static String joinedStringList(JsonWebToken jwt, String name, String emptyText) {
        List<String> parts = stringList(jwt, name);
        return parts.isEmpty() ? emptyText : String.join(", ", parts);
    }
}
