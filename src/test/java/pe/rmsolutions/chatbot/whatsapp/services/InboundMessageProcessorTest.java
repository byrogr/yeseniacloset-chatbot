package pe.rmsolutions.chatbot.whatsapp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;
import pe.rmsolutions.chatbot.whatsapp.repository.GraphApiClientException;
import pe.rmsolutions.chatbot.whatsapp.repository.OutboundMessenger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica el filtro por tipo, la pausa, el envío y el recorte de la respuesta.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class InboundMessageProcessorTest {

    private static final String PHONE = "51911111111";

    private ChatService chatService;
    private OutboundMessenger messenger;
    private InboundMessageProcessor processor;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        messenger = mock(OutboundMessenger.class);
        WhatsAppConfig config = mock(WhatsAppConfig.class);
        when(config.maxTextLength()).thenReturn(10);
        processor = new InboundMessageProcessor(chatService, messenger, config);
    }

    @Test
    void imagenNoSeRespondeNiLlamaAlAsistente() {
        processor.process(message("image", null));
        processor.process(message("audio", null));
        processor.process(message("sticker", null));

        verifyNoInteractions(chatService, messenger);
    }

    @Test
    void conversacionPausadaNoEnviaNada() {
        when(chatService.reply(PHONE, "hola")).thenReturn(new ChatReply(Optional.empty(), List.of(), false, true));

        processor.process(message("text", "hola"));

        verify(messenger, never()).sendText(anyString(), anyString());
    }

    @Test
    void textoNormalSeEnvia() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("Hola Gaby"));

        processor.process(message("text", "hola"));

        verify(messenger).sendText(PHONE, "Hola Gaby");
    }

    @Test
    void textoLargoSeTrunca() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("0123456789ABCDEF"));

        processor.process(message("text", "hola"));

        verify(messenger).sendText(PHONE, "0123456789");
    }

    @Test
    void elRecorteNoParteUnEmoji() {
        assertThat(InboundMessageProcessor.truncate("012345678👗x", 10)).isEqualTo("012345678");
        assertThat(InboundMessageProcessor.truncate("corto", 10)).isEqualTo("corto");
    }

    @Test
    void unFalloDeEnvioNoSePropaga() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("Hola"));
        doThrow(new GraphApiClientException(400, 131030)).when(messenger).sendText(any(), any());

        assertThatCode(() -> processor.process(message("text", "hola"))).doesNotThrowAnyException();
    }

    private static ChatReply reply(String text) {
        return new ChatReply(Optional.of(text), List.of("getAccountStatus"), false, false);
    }

    private static InboundMessage message(String type, String text) {
        return new InboundMessage("wamid.X", PHONE, type, text, Instant.EPOCH);
    }
}
