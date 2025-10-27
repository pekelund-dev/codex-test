package dev.pekelund.pklnd.receiptparser.local;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Minimal {@link ChatModel} implementation used for the {@code local-receipt-test}
 * profile so that Spring AI auto-configuration backs off and we avoid depending
 * on Google AI Gemini during local development.
 */
class NoopChatModel implements ChatModel {

    private static final String DISABLED_MESSAGE =
        "ChatModel is disabled for the local-receipt-test profile";

    @Override
    public ChatResponse call(Prompt prompt) {
        throw new UnsupportedOperationException(DISABLED_MESSAGE);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        DefaultChatOptions options = new DefaultChatOptions();
        options.setModel("local-noop");
        return options;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new UnsupportedOperationException(DISABLED_MESSAGE));
    }
}
