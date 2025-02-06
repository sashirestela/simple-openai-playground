package io.github.sashirestela.openai.playground;

import java.util.List;

import io.github.sashirestela.openai.OpenAI;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.common.content.ContentPart.ContentPartImageUrl;
import io.github.sashirestela.openai.common.content.ContentPart.ContentPartImageUrl.ImageUrl;
import io.github.sashirestela.openai.common.content.ContentPart.ContentPartText;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage.UserMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.support.Base64Util;
import io.github.sashirestela.openai.support.Base64Util.MediaType;

public class DemoVision {

    private SimpleOpenAI openai;
    private OpenAI.ChatCompletions chatService;
    private String model;

    public DemoVision() {
        openai = SimpleOpenAI.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
        chatService = openai.chatCompletions();
        model = "gpt-4o-mini";
    }

    public void demoCallChatWithVisionExternalImage() {
        var chatRequest = ChatRequest.builder()
                .model(model)
                .messages(List.of(
                        UserMessage.of(List.of(
                                ContentPartText.of(
                                        "What do you see in the image? Give in details in no more than 100 words."),
                                ContentPartImageUrl.of(ImageUrl.of(
                                        "https://upload.wikimedia.org/wikipedia/commons/e/eb/Machu_Picchu%2C_Peru.jpg"))))))
                .temperature(0.0)
                .maxCompletionTokens(500)
                .build();
        var chatResponse = chatService.createStream(chatRequest).join();
        chatResponse.forEach(this::processResponseChunk);
        System.out.println();
    }

    public void demoCallChatWithVisionLocalImage() {
        var chatRequest = ChatRequest.builder()
                .model(model)
                .messages(List.of(
                        UserMessage.of(List.of(
                                ContentPartText.of(
                                        "What do you see in the image? Give in details in no more than 100 words."),
                                ContentPartImageUrl.of(ImageUrl.of(
                                        Base64Util.encode("src/main/resources/machupicchu.jpg", MediaType.IMAGE)))))))
                .temperature(0.0)
                .maxCompletionTokens(500)
                .build();
        var chatResponse = chatService.createStream(chatRequest).join();
        chatResponse.forEach(this::processResponseChunk);
        System.out.println();
    }

    private void processResponseChunk(Chat responseChunk) {
        var choices = responseChunk.getChoices();
        if (!choices.isEmpty()) {
            var delta = choices.get(0).getMessage();
            if (delta.getContent() != null) {
                System.out.print(delta.getContent());
            }
            if (delta.getReasoningContent() != null) {
                System.out.print(delta.getReasoningContent());
            }
        }
        var usage = responseChunk.getUsage();
        if (usage != null && usage.getCompletionTokens() != 0) {
            System.out.println("\n");
            System.out.println(usage);
        }
    }

    public static void main(String[] args) {
        var demoVision = new DemoVision();
        demoVision.demoCallChatWithVisionExternalImage();
        demoVision.demoCallChatWithVisionLocalImage();
    }
}
