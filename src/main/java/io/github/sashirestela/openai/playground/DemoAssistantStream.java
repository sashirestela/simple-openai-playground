package io.github.sashirestela.openai.playground;

import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.OpenAIBeta.Assistants;
import io.github.sashirestela.openai.OpenAIBeta.Threads;
import io.github.sashirestela.openai.domain.assistant.AssistantRequest;
import io.github.sashirestela.openai.domain.assistant.Events;
import io.github.sashirestela.openai.domain.assistant.TextContent;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageDelta;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRequest;
import io.github.sashirestela.openai.domain.assistant.ThreadRequest;
import io.github.sashirestela.openai.domain.assistant.ThreadRunRequest;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRequest.Role;

public class DemoAssistantStream {

    private SimpleOpenAI openai;
    private Assistants assistantService;
    private Threads threadService;
    private String assistantId;
    private String threadId;

    public DemoAssistantStream() {
        openai = SimpleOpenAI.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        assistantService = openai.assistants();
        threadService = openai.threads();
    }

    public void createAssistant() {
        var assistant = assistantService.create(AssistantRequest.builder()
                .model("gpt-3.5-turbo-1106")
                .name("AI Expert")
                .instructions("You are an AI expert. When you are asked you answer with no less than 100 words.")
                .build()).join();
        assistantId = assistant.getId();

        System.out.println("-".repeat(80));
        System.out.println("Assistant was created with id: " + assistantId);
    }

    public void createThread() {
        var thread = threadService.create(ThreadRequest.builder()
                .message(ThreadMessageRequest.builder()
                        .role(Role.USER)
                        .content("What is the impact of the LLM on the software developer sector?")
                        .build())
                .build()).join();
        threadId = thread.getId();

        System.out.println("-".repeat(80));
        System.out.println("Thread was created with id: " + threadId);
    }

    public void createRunAndStream() {
        var stream = threadService.createRunStream(threadId, ThreadRunRequest.builder()
                .assistantId(assistantId)
                .build()).join();
        System.out.println("-".repeat(80));
        stream.filter(event -> event.getName().equals(Events.THREAD_MESSAGE_DELTA))
                .map(event -> ((TextContent) ((ThreadMessageDelta) event.getData())
                        .getDelta().getContent().get(0)).getValue())
                .forEach(System.out::print);
        System.out.println();
    }

    public void cleanAll() {
        var deletedThread = threadService.delete(threadId).join();
        var deletedAssistant = assistantService.delete(assistantId).join();

        System.out.println("-".repeat(80));
        System.out.println("Thread was deleted: " + deletedThread.getDeleted());
        System.out.println("Assistant was deleted: " + deletedAssistant.getDeleted());
    }

    public static void main(String[] args) {
        var demo = new DemoAssistantStream();
        demo.createAssistant();
        demo.createThread();
        demo.createRunAndStream();
        demo.cleanAll();
    }
}
