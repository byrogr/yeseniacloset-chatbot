package pe.rmsolutions.chatbot.whatsapp.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.rmsolutions.chatbot.whatsapp.model.InboundMessage;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Verifica el orden por celular, el paralelismo entre celulares y que un fallo no corte la cadena.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
class InboundDispatcherTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final InboundMessageProcessor processor = mock(InboundMessageProcessor.class);
    private final InboundDispatcher dispatcher = new InboundDispatcher(executor, processor);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void mismoCelularEnOrdenAunqueElPrimeroTardeMas() throws Exception {
        List<String> processed = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            InboundMessage message = invocation.getArgument(0);
            if (message.messageId().equals("m1")) {
                Thread.sleep(300);
            }
            processed.add(message.messageId());
            return null;
        }).when(processor).process(any());

        CompletableFuture<Void> f1 = dispatcher.dispatch(message("m1", "51911111111"));
        CompletableFuture<Void> f2 = dispatcher.dispatch(message("m2", "51911111111"));
        CompletableFuture<Void> f3 = dispatcher.dispatch(message("m3", "51911111111"));
        CompletableFuture.allOf(f1, f2, f3).get(5, TimeUnit.SECONDS);

        assertThat(processed).containsExactly("m1", "m2", "m3");
        assertThat(dispatcher.activeChains()).isZero();
    }

    @Test
    void celularesDistintosEnParalelo() throws Exception {
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean firstSawSecond = new AtomicBoolean();
        doAnswer(invocation -> {
            InboundMessage message = invocation.getArgument(0);
            if (message.from().equals("51911111111")) {
                // Solo termina a tiempo si el otro celular se procesa mientras este espera.
                firstSawSecond.set(secondStarted.await(2, TimeUnit.SECONDS));
            } else {
                secondStarted.countDown();
            }
            return null;
        }).when(processor).process(any());

        CompletableFuture<Void> a = dispatcher.dispatch(message("a1", "51911111111"));
        CompletableFuture<Void> b = dispatcher.dispatch(message("b1", "51922222222"));
        CompletableFuture.allOf(a, b).get(5, TimeUnit.SECONDS);

        assertThat(firstSawSecond).isTrue();
    }

    @Test
    void unaExcepcionNoCortaLaCadena() throws Exception {
        List<String> processed = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            InboundMessage message = invocation.getArgument(0);
            if (message.messageId().equals("m1")) {
                throw new IllegalStateException("boom");
            }
            processed.add(message.messageId());
            return null;
        }).when(processor).process(any());

        CompletableFuture<Void> f1 = dispatcher.dispatch(message("m1", "51911111111"));
        CompletableFuture<Void> f2 = dispatcher.dispatch(message("m2", "51911111111"));
        CompletableFuture.allOf(f1, f2).get(5, TimeUnit.SECONDS);

        assertThat(f1).isCompletedWithValue(null);
        assertThat(processed).containsExactly("m2");
    }

    private static InboundMessage message(String id, String from) {
        return new InboundMessage(id, from, "text", "hola", Instant.EPOCH);
    }
}
