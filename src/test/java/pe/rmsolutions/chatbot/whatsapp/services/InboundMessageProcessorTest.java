package pe.rmsolutions.chatbot.whatsapp.services;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.MutableClock;
import pe.rmsolutions.chatbot.agent.TestBotConfig;
import pe.rmsolutions.chatbot.agent.model.ChatReply;
import pe.rmsolutions.chatbot.agent.services.ChatService;
import pe.rmsolutions.chatbot.agent.services.ConversationPauseRegistry;
import pe.rmsolutions.chatbot.agent.services.HandoffMessages;
import pe.rmsolutions.chatbot.observability.services.BotMetrics;
import pe.rmsolutions.chatbot.observability.services.BotMetrics.IgnoreReason;
import pe.rmsolutions.chatbot.whatsapp.config.WhatsAppConfig;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;
import pe.rmsolutions.chatbot.whatsapp.repository.GraphApiClientException;
import pe.rmsolutions.chatbot.whatsapp.repository.OutboundMessenger;

import java.time.Duration;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica el orden de filtros (pausa, interruptor y lista de piloto, tipo), los avisos fijos, el envío y el
 * recorte de la respuesta, en modo número nuevo y en modo coexistencia.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class InboundMessageProcessorTest {

    private static final String PHONE = "51911111111";
    private static final String OTHER = "51922222222";
    private static final String LINK = "https://wa.me/" + TestBotConfig.OWNER_CONTACT_PHONE;
    private static final String NON_TEXT_NOTICE = "Hola, soy el asistente automático de Yesenia. Solo puedo leer "
            + "mensajes de texto. Escríbeme tu consulta o, si prefieres enviar audios o fotos, escríbele a Yesenia: "
            + LINK;
    private static final String REDIRECT_NOTICE = "Hola, soy el asistente automático de Yesenia. En este momento "
            + "no estoy atendiendo; escríbele a Yesenia: " + LINK;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T10:00:00Z"));
    private final ConversationPauseRegistry pauses = new ConversationPauseRegistry(clock);
    private final ChatService chatService = mock(ChatService.class);
    private final OutboundMessenger messenger = mock(OutboundMessenger.class);
    private final BotMetrics metrics = mock(BotMetrics.class);
    private final NoticeThrottle throttle = new NoticeThrottle(clock);

    @Test
    void audioRecibeElAvisoUnaVezCada12Horas() {
        InboundMessageProcessor processor = processor(TestBotConfig.redirectingToOwner());

        processor.process(message("audio", null));
        processor.process(message("image", null));
        clock.advance(Duration.ofHours(12));
        processor.process(message("sticker", null));

        verify(messenger, times(2)).sendText(PHONE, NON_TEXT_NOTICE);
        verifyNoInteractions(chatService);
    }

    @Test
    void enCoexistenciaElAvisoDiceQueLaDueniaLoRevisara() {
        processor(new TestBotConfig()).process(message("audio", null));

        verify(messenger).sendText(PHONE, "Hola, soy el asistente automático de Yesenia. "
                + "Por ahora solo puedo leer mensajes de texto; Yesenia revisará tu mensaje pronto.");
    }

    @Test
    void conversacionPausadaNoRecibeNingunAviso() {
        pauses.pause(PHONE, Duration.ofHours(1));
        InboundMessageProcessor processor = processor(TestBotConfig.redirectingToOwner().disabled());

        processor.process(message("audio", null));
        processor.process(message("text", "hola"));

        verifyNoInteractions(chatService, messenger);
    }

    @Test
    void botApagadoNoLlamaAlAsistenteYRedirigeUnaVez() {
        InboundMessageProcessor processor = processor(TestBotConfig.redirectingToOwner().disabled());

        processor.process(message("text", "hola"));
        processor.process(message("audio", null));
        processor.process(message("text", "cuanto debo?"));

        verify(messenger, times(1)).sendText(PHONE, REDIRECT_NOTICE);
        verify(messenger, times(1)).sendText(anyString(), anyString());
        verifyNoInteractions(chatService);
    }

    @Test
    void celularFueraDeLaListaNoLlamaAlAsistenteYRedirige() {
        InboundMessageProcessor processor = processor(TestBotConfig.redirectingToOwner().withAllowlist(OTHER));

        processor.process(message("text", "hola"));

        verify(messenger).sendText(PHONE, REDIRECT_NOTICE);
        verifyNoInteractions(chatService);
    }

    @Test
    void celularDeLaListaPasaAlAsistente() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("Hola Gaby"));
        InboundMessageProcessor processor =
                processor(TestBotConfig.redirectingToOwner().withAllowlist(OTHER, PHONE));

        processor.process(message("text", "hola"));

        verify(messenger).sendText(PHONE, "Hola Gaby");
    }

    @Test
    void enCoexistenciaApagadoOFueraDeLaListaCalla() {
        processor(new TestBotConfig().disabled()).process(message("text", "hola"));
        processor(new TestBotConfig().withAllowlist(OTHER)).process(message("text", "hola"));

        verifyNoInteractions(chatService, messenger);
    }

    @Test
    void conversacionPausadaPorElAsistenteNoEnviaNada() {
        when(chatService.reply(PHONE, "hola")).thenReturn(new ChatReply(Optional.empty(), List.of(), false, true));

        processor(new TestBotConfig()).process(message("text", "hola"));

        verify(messenger, never()).sendText(anyString(), anyString());
    }

    @Test
    void textoNormalSeEnvia() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("Hola Gaby"));

        processor(new TestBotConfig()).process(message("text", "hola"));

        verify(messenger).sendText(PHONE, "Hola Gaby");
    }

    @Test
    void textoLargoSeTrunca() {
        when(chatService.reply(PHONE, "hola")).thenReturn(reply("0123456789ABCDEF"));

        processor(new TestBotConfig()).process(message("text", "hola"));

        verify(messenger).sendText(PHONE, "0123456789");
    }

    @Test
    void registraLasMetricasDeMensajesIgnoradosYNoTexto() {
        processor(TestBotConfig.redirectingToOwner()).process(message("audio", null));
        processor(TestBotConfig.redirectingToOwner()).process(message("audio", null));
        processor(TestBotConfig.redirectingToOwner().disabled()).process(message("text", "hola"));
        processor(TestBotConfig.redirectingToOwner().withAllowlist(OTHER)).process(message("text", "hola"));

        verify(metrics, times(2)).nonTextMessage();
        verify(metrics).messageIgnored(IgnoreReason.DISABLED);
        verify(metrics).messageIgnored(IgnoreReason.NOT_ALLOWLISTED);
        // El segundo audio y la segunda redirección caen dentro de las 12 h.
        verify(metrics, times(2)).messageIgnored(IgnoreReason.NOTICE_SUPPRESSED);
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
        InboundMessageProcessor processor = processor(TestBotConfig.redirectingToOwner());

        assertThatCode(() -> processor.process(message("text", "hola"))).doesNotThrowAnyException();
        assertThatCode(() -> processor.process(message("audio", null))).doesNotThrowAnyException();
    }

    private InboundMessageProcessor processor(TestBotConfig botConfig) {
        WhatsAppConfig config = mock(WhatsAppConfig.class);
        when(config.maxTextLength()).thenReturn(10);
        return new InboundMessageProcessor(chatService, messenger, config, botConfig, pauses,
                new HandoffMessages(botConfig), throttle, metrics);
    }

    private static ChatReply reply(String text) {
        return new ChatReply(Optional.of(text), List.of("getAccountStatus"), false, false);
    }

    private static InboundMessage message(String type, String text) {
        return new InboundMessage("wamid.X", PHONE, type, text, Instant.EPOCH);
    }
}
