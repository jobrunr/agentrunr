package ai.javaclaw.chat.api;

import ai.javaclaw.chat.ChatChannel;
import ai.javaclaw.chat.ChatHtml;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ChatController {

    @Value("${jobrunr.dashboard.port:8081}")
    private int jobrunrDashboardPort;

    private final ChatChannel chatChannel;

    public ChatController(ChatChannel chatChannel) {
        this.chatChannel = chatChannel;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("jobrunrDashboardPort", jobrunrDashboardPort);

        List<String> agentIds = chatChannel.agentIds();
        String selectedAgentId = agentIds.contains(chatChannel.defaultAgentId()) ? chatChannel.defaultAgentId() : agentIds.getFirst();
        List<String> conversationIds = chatChannel.conversationIds(selectedAgentId);
        String selectedConversationId = conversationIds.getFirst();

        model.addAttribute("agentSelectorHtml", ChatHtml.agentSelector(agentIds, selectedAgentId));
        model.addAttribute("channelSelectorHtml", ChatHtml.conversationSelector(conversationIds, selectedConversationId));
        model.addAttribute("bubblesHtml", String.join(System.lineSeparator(), chatChannel.loadHistoryAsHtml(selectedAgentId, selectedConversationId)));
        model.addAttribute("inputAreaHtml", ChatHtml.chatInputArea(selectedAgentId, selectedConversationId));

        return "chat";
    }
}
