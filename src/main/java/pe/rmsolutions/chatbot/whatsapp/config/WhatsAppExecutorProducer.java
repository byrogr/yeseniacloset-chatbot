package pe.rmsolutions.chatbot.whatsapp.config;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor de hilos virtuales para procesar los mensajes entrantes fuera del hilo del webhook.
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public class WhatsAppExecutorProducer {

    public static final String INBOUND_EXECUTOR = "whatsapp-inbound";

    @Produces
    @Singleton
    @Named(INBOUND_EXECUTOR)
    public ExecutorService inboundExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public void close(@Disposes @Named(INBOUND_EXECUTOR) ExecutorService executor) {
        executor.shutdown();
    }
}
