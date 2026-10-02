package pe.rmsolutions.chatbot.sheets.model;

/**
 * Identifica una pestaña de campaña del Sheet (por ejemplo, {@code C-11-pacifika}).
 *
 * @author Roger Rojas - roger.rojas@rmsolutions.pe
 */
public record CampaignId(String title, int number, String catalog) {
}
