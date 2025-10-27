package dev.pekelund.pklnd.receiptparser.googleai;

/**
 * Minimal client interface for invoking Google AI Studio's Gemini API.
 */
public interface GeminiClient {

    /**
     * @return the default chat options configured for the client.
     */
    GoogleAiGeminiChatOptions getDefaultOptions();

    /**
     * Generates text using Gemini for the provided prompt and optional overrides.
     *
     * @param prompt the prompt to send to the model
     * @param overrides optional overrides for the default chat options; may be {@code null}
     * @return the generated text response from Gemini
     */
    String generateContent(String prompt, GoogleAiGeminiChatOptions overrides);
}
