package pe.rmsolutions.chatbot.agent.services;

import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.agent.TestBotConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class HandoffMessagesTest {

    private static final String LINK = "https://wa.me/51911222333";

    private final HandoffMessages coexistence = new HandoffMessages(new TestBotConfig());
    private final HandoffMessages redirect = new HandoffMessages(TestBotConfig.redirectingToOwner());

    @Test
    void coexistenciaMantieneLosTextosDeLaFase3() {
        assertThat(coexistence.redirectsToOwner()).isFalse();
        assertThat(coexistence.handoff()).isEqualTo("Yesenia te escribirá pronto.");
        assertThat(coexistence.unavailable())
                .isEqualTo("En este momento no puedo revisar tu pedido. Yesenia te escribirá pronto.");
        assertThat(coexistence.unregistered())
                .isEqualTo("Hola, Yesenia te escribirá pronto para ayudarte con tu pedido.");
        assertThat(coexistence.redirectNotice()).isEmpty();
        assertThat(coexistence.nonTextNotice()).isEqualTo("Hola, soy el asistente automático de Yesenia. "
                + "Por ahora solo puedo leer mensajes de texto; Yesenia revisará tu mensaje pronto.");
    }

    @Test
    void numeroNuevoMandaAlChatDeLaDueniaConElEnlace() {
        assertThat(redirect.redirectsToOwner()).isTrue();
        assertThat(redirect.handoff()).isEqualTo("Eso lo ve directamente Yesenia. Escríbele aquí: " + LINK);
        assertThat(redirect.unavailable()).isEqualTo("En este momento no puedo revisar tu pedido. "
                + "Inténtalo más tarde o escríbele a Yesenia: " + LINK);
        assertThat(redirect.unregistered())
                .isEqualTo("No encuentro pedidos con este número. Escríbele a Yesenia: " + LINK);
        assertThat(redirect.redirectNotice()).contains("Hola, soy el asistente automático de Yesenia. "
                + "En este momento no estoy atendiendo; escríbele a Yesenia: " + LINK);
        assertThat(redirect.nonTextNotice()).isEqualTo("Hola, soy el asistente automático de Yesenia. "
                + "Solo puedo leer mensajes de texto. Escríbeme tu consulta o, si prefieres enviar audios o fotos, "
                + "escríbele a Yesenia: " + LINK);
    }

    @Test
    void ningunTextoDelModoNumeroNuevoPrometeQueLaDueniaEscribira() {
        assertThat(java.util.stream.Stream.of(redirect.handoff(), redirect.unavailable(), redirect.unregistered(),
                redirect.redirectNotice().orElseThrow(), redirect.nonTextNotice()))
                .allSatisfy(text -> assertThat(text).doesNotContain("te escribirá").doesNotContain("revisará")
                        .endsWith(LINK));
    }
}
