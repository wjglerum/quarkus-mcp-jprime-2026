package nl.lunatech.jprime.chat.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import io.quarkiverse.playwright.InjectPlaywright;
import io.quarkiverse.playwright.WithPlaywright;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ChatPlaywrightTest.LiveOidc.class)
@WithPlaywright
class ChatPlaywrightTest {

    public static class LiveOidc implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.oidc.tenant-enabled", "true",
                    "quarkus.keycloak.devservices.enabled", "true");
        }
    }

    @InjectPlaywright
    BrowserContext context;

    @TestHTTPResource("/")
    URL chatBase;

    private Page freshPage() {
        BrowserContext isolated = context.browser().newContext();
        Page page = isolated.newPage();
        page.setDefaultTimeout(60_000);
        page.setDefaultNavigationTimeout(60_000);
        return page;
    }

    @Test
    void anonymousRequestRedirectsToKeycloakLogin() {
        Page page = freshPage();
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        assertThat(page.locator("#username").isVisible()).isTrue();
        assertThat(page.locator("#kc-login").isVisible()).isTrue();
    }

    @Test
    void willemJanLoginRendersQuteShell() {
        Page page = freshPage();
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        page.locator("#username").fill("willem.jan");
        page.locator("#password").fill("willem.jan");
        page.locator("#kc-login").click();

        page.waitForURL("**" + chatBase.getHost() + ":" + chatBase.getPort() + "/**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        Locator topbar = page.locator("header.topbar");
        assertThat(topbar.locator(".brand-title").innerText()).contains("Practical MCP Security in Action");
        assertThat(topbar.locator(".who").innerText()).contains("Willem Jan Glerum");

        assertThat(page.locator(".identity").innerText())
                .contains("willem.jan")
                .contains("attendee")
                .contains("speaker");

        Locator heroWords = page.locator(".hero-empty .hero-word");
        assertThat(heroWords.count()).isEqualTo(3);
        assertThat(heroWords.nth(0).innerText()).isEqualTo("Who.");
        assertThat(heroWords.nth(1).innerText()).isEqualTo("What.");
        assertThat(heroWords.nth(2).innerText()).isEqualTo("Provable.");

        assertThat(page.locator(".quick-prompt").count()).isGreaterThanOrEqualTo(5);
        assertThat(page.locator(".quick-prompt[data-tier=\"step-up\"]").count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void stepUpRedirectCarriesAcrValuesAndOtpCompletesMfa() {
        Page page = freshPage();
        // regular password login establishes a session without the MFA acr
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        page.locator("#username").fill("willem.jan");
        page.locator("#password").fill("willem.jan");
        page.locator("#kc-login").click();
        page.waitForURL("**" + chatBase.getHost() + ":" + chatBase.getPort() + "/**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        // the step-up endpoint requires the MFA acr; Quarkus must redirect to Keycloak
        // with acr_values so the conditional flow asks for the OTP (Keycloak skips the
        // password thanks to the SSO session)
        page.navigate(chatBase + "step-up");
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        assertThat(page.url()).contains("acr_values=urn%3Ajprime%3Amfa");

        page.locator("#otp").fill(totp("jprimemcp2026stepupseed"));
        page.locator("#kc-login").click();

        page.waitForURL("**/step-up**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        assertThat(page.locator("body").innerText()).contains("MFA complete");
    }

    /** RFC 6238 TOTP matching the realm OTP policy: HmacSHA1, 6 digits, 30 second period. */
    private static String totp(String seed) {
        try {
            long counter = Instant.now().getEpochSecond() / 30;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0xf;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void attendeeLoginRendersQuickPromptsWithStepUpTier() {
        Page page = freshPage();
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        page.locator("#username").fill("attendee");
        page.locator("#password").fill("attendee");
        page.locator("#kc-login").click();

        page.waitForURL("**" + chatBase.getHost() + ":" + chatBase.getPort() + "/**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        assertThat(page.locator(".quick-prompt").count()).isGreaterThanOrEqualTo(5);
        assertThat(page.locator(".quick-prompt[data-tier=\"step-up\"]").count()).isGreaterThanOrEqualTo(1);
    }
}
