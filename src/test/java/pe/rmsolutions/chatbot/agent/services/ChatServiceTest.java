package pe.rmsolutions.chatbot.agent.services;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.account.services.AccountStatusService;
import pe.rmsolutions.chatbot.agent.FixtureAccounts;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.repository.ExpiringChatMemoryStore;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Turnos completos con el modelo simulado ({@code @InjectMock OrderAssistant}) y el fixture de la Fase 1.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
@QuarkusTest
class ChatServiceTest {

    private static final String PHONE = FixtureAccounts.GABY;
    private static final String PRESENTATION = "Hola, soy el asistente automático de Yesenia.\n";

    @InjectMock
    OrderAssistant assistant;

    @Inject
    ChatService chatService;
    @Inject
    OrderTools orderTools;
    @Inject
    ExpiringChatMemoryStore memoryStore;
    @Inject
    ConversationPauseRegistry pauses;
    @Inject
    AccountStatusFormatter formatter;
    @Inject
    AccountStatusService accountStatusService;

    @BeforeEach
    void reset() {
        memoryStore.deleteMessages(PHONE);
        pauses.resume(PHONE);
    }

    @Test
    void conversacionPausadaNoLlamaAlAsistente() {
        pauses.pause(PHONE, Duration.ofHours(1));

        ChatReply reply = chatService.reply(PHONE, "hola");

        assertThat(reply.text()).isEmpty();
        assertThat(reply.paused()).isTrue();
        verify(assistant, never()).chat(anyString(), anyString(), anyString());
    }

    @Test
    void celularInvalidoNoResponde() {
        assertThat(chatService.reply("12345", "hola").text()).isEmpty();
        verify(assistant, never()).chat(anyString(), anyString(), anyString());
    }

    @Test
    void primerMensajeAntePoneLaPresentacionYElSegundoNo() {
        answerWithoutTools("¡Hola Gaby! ¿En qué te ayudo?");

        ChatReply first = chatService.reply(PHONE, "hola");
        ChatReply second = chatService.reply(PHONE, "hola de nuevo");

        assertThat(first.text()).contains(PRESENTATION + "¡Hola Gaby! ¿En qué te ayudo?");
        assertThat(second.text()).contains("¡Hola Gaby! ¿En qué te ayudo?");
        assertThat(first.toolsUsed()).isEmpty();
        assertThat(first.guardTriggered()).isFalse();
        verify(assistant, org.mockito.Mockito.times(2)).chat(eq(PHONE), anyString(), eq("Yesenia"));
    }

    @Test
    void montoCorrectoDeLaToolPasaLaGuardia() {
        answerCallingAccountTool("Tu total es S/ 175,53");

        ChatReply reply = chatService.reply(PHONE, "cuanto es lo mio?");

        assertThat(reply.text()).contains(PRESENTATION + "Tu total es S/ 175,53");
        assertThat(reply.toolsUsed()).containsExactly("getAccountStatus");
        assertThat(reply.guardTriggered()).isFalse();
    }

    @Test
    void montoInventadoSeReemplazaPorElFormateador() {
        answerCallingAccountTool("Tu total es S/ 150,00");

        ChatReply reply = chatService.reply(PHONE, "cuanto es lo mio?");

        assertThat(reply.guardTriggered()).isTrue();
        assertThat(reply.toolsUsed()).containsExactly("getAccountStatus");
        assertThat(reply.text()).contains(PRESENTATION + gabyFormatted());
    }

    @Test
    void montosSinLlamarLaToolUsanElServicioDirectamente() {
        answerWithoutTools("Tu total es S/ 175,53");

        ChatReply reply = chatService.reply(PHONE, "cuanto es lo mio?");

        assertThat(reply.guardTriggered()).isTrue();
        assertThat(reply.toolsUsed()).isEmpty();
        assertThat(reply.text()).contains(PRESENTATION + gabyFormatted());
    }

    @Test
    void falloDelModeloDevuelveTextoFijoYPausa() {
        when(assistant.chat(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));

        ChatReply reply = chatService.reply(PHONE, "cuanto es lo mio?");

        assertThat(reply.text()).contains(PRESENTATION
                + "En este momento no puedo revisar tu pedido. Yesenia te escribirá pronto.");
        assertThat(reply.paused()).isTrue();
        assertThat(pauses.isPaused(PHONE)).isTrue();
    }

    private String gabyFormatted() {
        return formatter.format(accountStatusService.getAccountStatus(PHONE), "Yesenia");
    }

    private void answerWithoutTools(String content) {
        when(assistant.chat(eq(PHONE), anyString(), any())).thenAnswer(inv -> {
            remember(inv.getArgument(1), content);
            return Result.<String>builder().content(content).toolExecutions(List.of()).build();
        });
    }

    private void answerCallingAccountTool(String content) {
        when(assistant.chat(eq(PHONE), anyString(), any())).thenAnswer(inv -> {
            String toolResult = orderTools.getAccountStatus(PHONE);
            remember(inv.getArgument(1), content);
            ToolExecution execution = org.mockito.Mockito.mock(ToolExecution.class);
            when(execution.request()).thenReturn(
                    ToolExecutionRequest.builder().id("1").name("getAccountStatus").arguments("{}").build());
            when(execution.result()).thenReturn(toolResult);
            return Result.<String>builder().content(content).toolExecutions(List.of(execution)).build();
        });
    }

    private void remember(String userMessage, String aiMessage) {
        var messages = new java.util.ArrayList<>(memoryStore.getMessages(PHONE));
        messages.add(UserMessage.from(userMessage));
        messages.add(AiMessage.from(aiMessage));
        memoryStore.updateMessages(PHONE, messages);
    }
}
