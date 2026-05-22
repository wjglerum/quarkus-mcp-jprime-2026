package nl.lunatech.jprime.chat.llm;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ProviderRegistry {

    private static final Logger LOG = Logger.getLogger(ProviderRegistry.class);

    public static final String SCRIPTED = "scripted";
    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";
    public static final String OLLAMA = "ollama";

    @Inject
    @ModelName("claude")
    Instance<ChatModel> anthropicChat;

    @Inject
    @ModelName("gpt")
    Instance<ChatModel> openaiChat;

    @Inject
    @ModelName("llama")
    Instance<ChatModel> ollamaChat;

    @ConfigProperty(name = "chat.llm.initial-provider", defaultValue = SCRIPTED)
    String initialProvider;

    @ConfigProperty(name = "quarkus.langchain4j.anthropic.claude.api-key", defaultValue = "not-set")
    String anthropicKey;

    @ConfigProperty(name = "quarkus.langchain4j.openai.gpt.api-key", defaultValue = "not-set")
    String openaiKey;

    @ConfigProperty(name = "quarkus.langchain4j.anthropic.claude.chat-model.model-name", defaultValue = "")
    String anthropicModel;

    @ConfigProperty(name = "quarkus.langchain4j.openai.gpt.chat-model.model-name", defaultValue = "")
    String openaiModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.llama.chat-model.model-id", defaultValue = "")
    String ollamaModel;

    private volatile String active = SCRIPTED;

    @PostConstruct
    void init() {
        String requested = initialProvider == null ? SCRIPTED : initialProvider.toLowerCase(Locale.ROOT);
        if (isKnown(requested) && (SCRIPTED.equals(requested) || isAvailable(requested))) {
            active = requested;
        } else {
            active = SCRIPTED;
        }
        LOG.infof("ProviderRegistry active provider: %s (anthropic=%s, openai=%s, ollama=%s)",
                active, isAvailable(ANTHROPIC), isAvailable(OPENAI), isAvailable(OLLAMA));
    }

    public List<ProviderInfo> list() {
        List<ProviderInfo> out = new java.util.ArrayList<>();
        out.add(new ProviderInfo(SCRIPTED, true, "deterministic intent matcher", SCRIPTED.equals(active)));
        out.add(new ProviderInfo(ANTHROPIC, isAvailable(ANTHROPIC), anthropicModel, ANTHROPIC.equals(active)));
        out.add(new ProviderInfo(OPENAI, isAvailable(OPENAI), openaiModel, OPENAI.equals(active)));
        out.add(new ProviderInfo(OLLAMA, isAvailable(OLLAMA), ollamaModel, OLLAMA.equals(active)));
        return out;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", active);
        m.put("providers", list());
        return m;
    }

    public String activeProvider() {
        return active;
    }

    public boolean isLlmActive() {
        return !SCRIPTED.equals(active);
    }

    public synchronized SwitchResult setActive(String requested) {
        if (requested == null) return new SwitchResult(false, active, "no provider given");
        String r = requested.toLowerCase(Locale.ROOT);
        if (!isKnown(r)) return new SwitchResult(false, active, "unknown provider: " + requested);
        if (!SCRIPTED.equals(r) && !isAvailable(r)) {
            return new SwitchResult(false, active, "provider_unavailable: " + r);
        }
        active = r;
        LOG.infof("ProviderRegistry switched to %s", active);
        return new SwitchResult(true, active, null);
    }

    public Optional<ChatModel> activeChatModel() {
        return switch (active) {
            case ANTHROPIC -> resolve(anthropicChat);
            case OPENAI -> resolve(openaiChat);
            case OLLAMA -> resolve(ollamaChat);
            default -> Optional.empty();
        };
    }

    private static boolean isKnown(String p) {
        return SCRIPTED.equals(p) || ANTHROPIC.equals(p) || OPENAI.equals(p) || OLLAMA.equals(p);
    }

    public boolean isAvailable(String provider) {
        return switch (provider) {
            case SCRIPTED -> true;
            case ANTHROPIC -> hasKey(anthropicKey) && resolve(anthropicChat).isPresent();
            case OPENAI -> hasKey(openaiKey) && resolve(openaiChat).isPresent();
            case OLLAMA -> resolve(ollamaChat).isPresent();
            default -> false;
        };
    }

    private static boolean hasKey(String key) {
        return key != null && !key.isBlank() && !"not-set".equals(key) && !"dummy".equals(key);
    }

    private static Optional<ChatModel> resolve(Instance<ChatModel> inst) {
        try {
            if (inst != null && inst.isResolvable()) {
                return Optional.of(inst.get());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public record ProviderInfo(String id, boolean available, String model, boolean active) {}

    public record SwitchResult(boolean ok, String active, String error) {}
}
