package io.github.sashirestela.openai.playground;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.github.sashirestela.cleverclient.Event;
import io.github.sashirestela.openai.OpenAIBeta;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.domain.assistant.AssistantFunction;
import io.github.sashirestela.openai.domain.assistant.AssistantRequest;
import io.github.sashirestela.openai.domain.assistant.AssistantTool;
import io.github.sashirestela.openai.domain.assistant.Events;
import io.github.sashirestela.openai.domain.assistant.TextContent;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageDelta;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRequest;
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRequest.Role;
import io.github.sashirestela.openai.domain.assistant.ThreadRequest;
import io.github.sashirestela.openai.domain.assistant.ThreadRun;
import io.github.sashirestela.openai.domain.assistant.ThreadRunRequest;
import io.github.sashirestela.openai.domain.assistant.ToolOutputSubmission;
import io.github.sashirestela.openai.domain.chat.tool.ChatFunction;
import io.github.sashirestela.openai.function.FunctionExecutor;
import io.github.sashirestela.openai.function.Functional;

public class DemoAssistantFunctionStream {

    private SimpleOpenAI openai;
    private OpenAIBeta.Assistants assistantService;
    private OpenAIBeta.Threads threadService;
    private FunctionExecutor functionExecutor;
    private String assistantId;
    private String threadId;

    public DemoAssistantFunctionStream() {
        openai = SimpleOpenAI.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        assistantService = openai.assistants();
        threadService = openai.threads();
        functionExecutor = new FunctionExecutor();
    }

    public void prepareConversation() {
        List<ChatFunction> functionList = new ArrayList<>();
        functionList.add(ChatFunction.builder()
                .name("getCurrentTemperature")
                .description("Get the current temperature for a specific location")
                .functionalClass(CurrentTemperature.class)
                .build());
        functionList.add(ChatFunction.builder()
                .name("getRainProbability")
                .description("Get the probability of rain for a specific location")
                .functionalClass(RainProbability.class)
                .build());
        functionExecutor.enrollFunctions(functionList);

        var assistant = assistantService.create(AssistantRequest.builder()
                .name("World Assistant")
                .model("gpt-4-turbo")
                .instructions(
                        "You are a skilled tutor on geo-politic topics.")
                .tool(AssistantTool.builder().function(AssistantFunction.function(functionList.get(0))).build())
                .tool(AssistantTool.builder().function(AssistantFunction.function(functionList.get(1))).build())
                .build()).join();
        assistantId = assistant.getId();
        System.out.println("Assistant was created with id: " + assistantId);

        var thread = threadService.create(ThreadRequest.builder().build()).join();
        threadId = thread.getId();
        System.out.println("Thread was created with id: " + threadId);
        System.out.println();
    }

    public void runConversation() {
        var myMessage = "Hi, can you help me?";
        System.out.println(myMessage);
        while (!myMessage.toLowerCase().equals("exit")) {
            threadService.createMessage(threadId, ThreadMessageRequest.builder()
                    .role(Role.USER)
                    .content(myMessage)
                    .build());
            var runStream = threadService.createRunStream(threadId, ThreadRunRequest.builder()
                    .assistantId(assistantId)
                    .build()).join();
            handleRunEvents(runStream);
            myMessage = System.console().readLine("\nAsk a question (or write 'exit' to finish): ");
        }
    }

    private void handleRunEvents(Stream<Event> runStream) {
        runStream.forEach(event -> {
            switch (event.getName()) {
                case Events.THREAD_RUN_CREATED:
                case Events.THREAD_RUN_COMPLETED:
                case Events.THREAD_RUN_REQUIRES_ACTION:
                    var run = (ThreadRun) event.getData();
                    System.out.println("=====>> Thread Run: id=" + run.getId() + ", status=" + run.getStatus());
                    if (run.getStatus().equals("requires_action")) {
                        var toolCalls = run.getRequiredAction().getSubmitToolOutputs().getToolCalls();
                        var toolOutputs = functionExecutor.executeAll(toolCalls);
                        var runSubmitToolStream = threadService.submitToolOutputsStream(threadId, run.getId(),
                                ToolOutputSubmission.builder()
                                        .toolOutputs(toolOutputs)
                                        .stream(true)
                                        .build())
                                .join();
                        handleRunEvents(runSubmitToolStream);
                    }
                    break;
                case Events.THREAD_MESSAGE_DELTA:
                    var msgDelta = (ThreadMessageDelta) event.getData();
                    var content = msgDelta.getDelta().getContent().get(0);
                    if (content instanceof TextContent) {
                        var textContent = (TextContent) content;
                        System.out.print(textContent.getValue());
                    }
                    break;
                case Events.THREAD_MESSAGE_COMPLETED:
                    System.out.println();
                    break;
                default:
                    break;
            }
        });
    }

    public void cleanConversation() {
        var deletedThread = threadService.delete(threadId).join();
        var deletedAssistant = assistantService.delete(assistantId).join();

        System.out.println("Thread was deleted: " + deletedThread.getDeleted());
        System.out.println("Assistant was deleted: " + deletedAssistant.getDeleted());
    }

    public static void main(String[] args) {
        var demo = new DemoAssistantFunctionStream();
        demo.prepareConversation();
        demo.runConversation();
        demo.cleanConversation();
    }

    public static class CurrentTemperature implements Functional {
        @JsonPropertyDescription("The city and state, e.g., San Francisco, CA")
        @JsonProperty(required = true)
        public String location;

        @JsonPropertyDescription("The temperature unit to use. Infer this from the user's location.")
        @JsonProperty(required = true)
        public String unit;

        @Override
        public Object execute() {
            return Math.random() * 45;
        }
    }

    public static class RainProbability implements Functional {
        @JsonPropertyDescription("The city and state, e.g., San Francisco, CA")
        @JsonProperty(required = true)
        public String location;

        @Override
        public Object execute() {
            return Math.random() * 100;
        }
    }

}
