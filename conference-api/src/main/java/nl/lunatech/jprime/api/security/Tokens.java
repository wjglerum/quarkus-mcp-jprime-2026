package nl.lunatech.jprime.api.security;

import org.eclipse.microprofile.jwt.JsonWebToken;

/** Reads the acr/amr step-up signals off an access token, the same way the MCP server does. */
public final class Tokens {

    private Tokens() {}

    public static String acr(JsonWebToken jwt) {
        Object acr = jwt == null ? null : jwt.getClaim("acr");
        return acr == null ? null : String.valueOf(acr);
    }

    /** The authorized party (azp): the OAuth client the token was issued to. */
    public static String azp(JsonWebToken jwt) {
        Object azp = jwt == null ? null : jwt.getClaim("azp");
        return azp == null ? null : String.valueOf(azp);
    }

    /** The token issuer (iss): the authorization server that minted the token. */
    public static String iss(JsonWebToken jwt) {
        String iss = jwt == null ? null : jwt.getIssuer();
        return iss == null || iss.isBlank() ? null : iss;
    }

    /** The amr values comma-joined (no spaces), or null when absent. */
    public static String amr(JsonWebToken jwt) {
        Object amr = jwt == null ? null : jwt.getClaim("amr");
        if (amr instanceof Iterable<?> values) {
            StringBuilder sb = new StringBuilder();
            for (Object value : values) {
                if (sb.length() > 0) sb.append(',');
                sb.append(value);
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        return amr == null ? null : String.valueOf(amr);
    }

    public static boolean hasStrongAcr(JsonWebToken jwt) {
        String acr = acr(jwt);
        if ("2".equals(acr) || "urn:mace:incommon:iap:silver".equals(acr)) {
            return true;
        }
        String amr = amr(jwt);
        if (amr == null) return false;
        for (String value : amr.split(",")) {
            if ("mfa".equals(value) || "otp".equals(value)) return true;
        }
        return false;
    }
}
