package io.github.sashirestela.openai.playground;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import io.github.sashirestela.openai.OpenAI;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.domain.chat.content.ContentPartImage;
import io.github.sashirestela.openai.domain.chat.content.ContentPartText;
import io.github.sashirestela.openai.domain.chat.content.ImageUrl;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgUser;

public class DemoVision {

    private SimpleOpenAI openai;
    private OpenAI.ChatCompletions chatService;

    public DemoVision() {
        openai = SimpleOpenAI.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
        chatService = openai.chatCompletions();
    }

    public void demoCallChatWithVisionExternalImage() {
        var chatRequest = ChatRequest.builder()
                .model("gpt-4-turbo-2024-04-09")
                .messages(List.of(
                        new ChatMsgUser(List.of(
                                new ContentPartText(
                                        "What do you see in the image? Give in details in no more than 100 words."),
                                new ContentPartImage(new ImageUrl(
                                        "https://upload.wikimedia.org/wikipedia/commons/e/eb/Machu_Picchu%2C_Peru.jpg"))))))
                .temperature(0.0)
                .maxTokens(500)
                .build();
        var chatResponse = chatService.createStream(chatRequest).join();
        chatResponse.filter(chatResp -> chatResp.firstContent() != null)
                .map(chatResp -> chatResp.firstContent())
                .forEach(System.out::print);
        System.out.println();
    }

    public void demoCallChatWithVisionLocalImage() {
        var chatRequest = ChatRequest.builder()
                .model("gpt-4-turbo-2024-04-09")
                .messages(List.of(
                        new ChatMsgUser(List.of(
                                new ContentPartText(
                                        "What do you see in the image? Give in details in no more than 100 words."),
                                new ContentPartImage(loadImageAsBase64("src/main/resources/machupicchu.jpg"))))))
                .temperature(0.0)
                .maxTokens(500)
                .build();
        var chatResponse = chatService.createStream(chatRequest).join();
        chatResponse.filter(chatResp -> chatResp.firstContent() != null)
                .map(chatResp -> chatResp.firstContent())
                .forEach(System.out::print);
        System.out.println();
    }

    private ImageUrl loadImageAsBase64(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);
            String base64String = Base64.getEncoder().encodeToString(imageBytes);
            var extension = imagePath.substring(imagePath.lastIndexOf('.') + 1);
            var prefix = "data:image/" + extension + ";base64,";
            return new ImageUrl(prefix + base64String);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        var demoVision = new DemoVision();
        demoVision.demoCallChatWithVisionExternalImage();
        demoVision.demoCallChatWithVisionLocalImage();
    }
}
