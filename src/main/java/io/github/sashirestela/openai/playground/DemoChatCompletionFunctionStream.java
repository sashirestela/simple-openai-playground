package io.github.sashirestela.openai.playground;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.sashirestela.openai.OpenAI;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.domain.chat.ChatResponse;
import io.github.sashirestela.openai.domain.chat.Choice;
import io.github.sashirestela.openai.domain.chat.message.ChatMsg;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgAssistant;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgResponse;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgSystem;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgTool;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgUser;
import io.github.sashirestela.openai.domain.chat.tool.ChatFunction;
import io.github.sashirestela.openai.domain.chat.tool.ChatToolCall;
import io.github.sashirestela.openai.function.FunctionExecutor;
import io.github.sashirestela.openai.function.Functional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DemoChatCompletionFunctionStream {

    private SimpleOpenAI openai;
    private OpenAI.ChatCompletions chatService;
    private FunctionExecutor functionExecutor;
    private List<ChatMsg> messages;

    private int indexTool;
    private StringBuilder content;
    private StringBuilder functionArgs;

    public DemoChatCompletionFunctionStream() {
        openai = SimpleOpenAI.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        chatService = openai.chatCompletions();
        functionExecutor = new FunctionExecutor();
        messages = new ArrayList<>();
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
        messages.add(new ChatMsgSystem("You are a skilled tutor on geo-politic topics."));
    }

    public void runConversation() {
        var userMessage = "Hi, can you help me?";
        System.out.println(userMessage);
        messages.add(new ChatMsgUser(userMessage));
        while (!userMessage.toLowerCase().equals("exit")) {
            var chatResponseStream = chatService.createStream(ChatRequest.builder()
                    .model("gpt-4-turbo")
                    .messages(messages)
                    .tools(functionExecutor.getToolFunctions())
                    .temperature(0.2)
                    .stream(true)
                    .build()).join();

            indexTool = -1;
            content = new StringBuilder();
            functionArgs = new StringBuilder();

            var response = getResponse(chatResponseStream);

            if (response.getMessage().getContent() != null) {
                messages.add(new ChatMsgAssistant(response.getMessage().getContent()));
            }
            if (response.getFinishReason().equals("tool_calls")) {
                messages.add(response.getMessage());
                for (var chatToollCall : response.getMessage().getToolCalls()) {
                    var result = functionExecutor.execute(chatToollCall.getFunction());
                    messages.add(new ChatMsgTool(result.toString(), chatToollCall.getId()));
                }
                continue;
            }
            userMessage = System.console().readLine("\n\nAsk a question (or write 'exit' to finish): ");
            messages.add(new ChatMsgUser(userMessage));
        }
    }

    private Choice getResponse(Stream<ChatResponse> chatResponseStream) {
        var choice = new Choice();
        choice.setIndex(0);
        var chatMsgResponse = new ChatMsgResponse();
        List<ChatToolCall> toolCalls = new ArrayList<>();

        chatResponseStream.forEach(chatResponseChunk -> {
            var innerChoice = chatResponseChunk.getChoices().get(0);
            var delta = innerChoice.getMessage();
            if (delta.getRole() != null) {
                chatMsgResponse.setRole(delta.getRole());
            } else if (delta.getContent() != null && !delta.getContent().isEmpty()) {
                content.append(delta.getContent());
                System.out.print(delta.getContent());
            } else if (delta.getToolCalls() != null) {
                var toolCall = delta.getToolCalls().get(0);
                if (toolCall.getIndex() != indexTool) {
                    if (toolCalls.size() > 0) {
                        toolCalls.get(toolCalls.size() - 1).getFunction().setArguments(functionArgs.toString());
                        functionArgs = new StringBuilder();
                    }
                    toolCalls.add(toolCall);
                    indexTool++;
                } else {
                    functionArgs.append(toolCall.getFunction().getArguments());
                }
            } else {
                if (content.length() > 0) {
                    chatMsgResponse.setContent(content.toString());
                }
                if (toolCalls.size() > 0) {
                    toolCalls.get(toolCalls.size() - 1).getFunction().setArguments(functionArgs.toString());
                    chatMsgResponse.setToolCalls(toolCalls);
                }
                choice.setMessage(chatMsgResponse);
                choice.setFinishReason(innerChoice.getFinishReason());
            }
        });
        return choice;
    }

    public static void main(String[] args) {
        var demo = new DemoChatCompletionFunctionStream();
        demo.prepareConversation();
        demo.runConversation();
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
