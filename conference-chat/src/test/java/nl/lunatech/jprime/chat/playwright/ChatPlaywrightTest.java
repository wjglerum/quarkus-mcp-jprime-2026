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

import java.net.URL;
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

    @Test
    void anonymousRequestRedirectsToKeycloakLogin() {
        Page page = context.newPage();
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        assertThat(page.locator("#username").isVisible()).isTrue();
        assertThat(page.locator("#kc-login").isVisible()).isTrue();
    }

    @Test
    void willemJanLoginRendersQuteShell() {
        Page page = context.newPage();
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
    void attendee1LoginRendersQuickPromptsWithStepUpTier() {
        Page page = context.newPage();
        page.navigate(chatBase.toString());
        page.waitForURL("**/realms/jprime/protocol/openid-connect/auth**");
        page.locator("#username").fill("attendee1");
        page.locator("#password").fill("attendee1");
        page.locator("#kc-login").click();

        page.waitForURL("**" + chatBase.getHost() + ":" + chatBase.getPort() + "/**");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        assertThat(page.locator(".quick-prompt").count()).isGreaterThanOrEqualTo(5);
        assertThat(page.locator(".quick-prompt[data-tier=\"step-up\"]").count()).isGreaterThanOrEqualTo(1);
    }
}
