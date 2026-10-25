package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class NotificationRendererTest {

    private NotificationRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        org.thymeleaf.spring6.SpringTemplateEngine templateEngine = new org.thymeleaf.spring6.SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        renderer = new NotificationRenderer(templateEngine);
        ReflectionTestUtils.setField(renderer, "webBaseUrl", "http://localhost:5173");
    }

    @Test
    @DisplayName("Should render booking-confirmed (CREATED) template with fee and tracking link")
    void shouldRenderCreatedTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1001", ParcelStatus.CREATED, "Alice Silva", Map.of("deliveryFee", "LKR 550.00"));

        assertNotNull(email.subject());
        assertTrue(email.subject().contains("SD-1001"));
        assertTrue(email.subject().contains("booked"));

        assertFalse(email.htmlBody().contains("${"), "Template should have no unresolved expressions");
        assertTrue(email.htmlBody().contains("Alice Silva"));
        assertTrue(email.htmlBody().contains("SD-1001"));
        assertTrue(email.htmlBody().contains("LKR 550.00"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1001"));

        assertTrue(email.textBody().contains("SD-1001"));
        assertTrue(email.textBody().contains("booked successfully"));
    }

    @Test
    @DisplayName("Should render picked-up (PICKED_UP) template")
    void shouldRenderPickedUpTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1002", ParcelStatus.PICKED_UP, "Bob Perera", null);

        assertFalse(email.htmlBody().contains("${"));
        assertTrue(email.htmlBody().contains("Bob Perera"));
        assertTrue(email.htmlBody().contains("SD-1002"));
        assertTrue(email.htmlBody().contains("picked up"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1002"));
    }

    @Test
    @DisplayName("Should render in-transit (IN_TRANSIT) template")
    void shouldRenderInTransitTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1003", ParcelStatus.IN_TRANSIT, "Charlie", null);

        assertFalse(email.htmlBody().contains("${"));
        assertTrue(email.htmlBody().contains("SD-1003"));
        assertTrue(email.htmlBody().contains("in transit"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1003"));
    }

    @Test
    @DisplayName("Should render out-for-delivery (OUT_FOR_DELIVERY) template")
    void shouldRenderOutForDeliveryTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1004", ParcelStatus.OUT_FOR_DELIVERY, "Diana", null);

        assertFalse(email.htmlBody().contains("${"));
        assertTrue(email.htmlBody().contains("SD-1004"));
        assertTrue(email.htmlBody().contains("arrive today"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1004"));
    }

    @Test
    @DisplayName("Should render delivered (DELIVERED) template with receipt download link")
    void shouldRenderDeliveredTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1005", ParcelStatus.DELIVERED, "Edward", null);

        assertFalse(email.htmlBody().contains("${"), "Delivered template should have no unresolved expressions");
        assertTrue(email.htmlBody().contains("SD-1005"));
        assertTrue(email.htmlBody().contains("delivered"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/api/v1/parcels/SD-1005/receipt"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1005"));

        assertTrue(email.textBody().contains("http://localhost:5173/api/v1/parcels/SD-1005/receipt"));
    }

    @Test
    @DisplayName("Should render delivery-failed (FAILED) template with reason and next steps")
    void shouldRenderFailedTemplate() {
        NotificationRenderer.RenderedEmail email = renderer.render(
                "SD-1006", ParcelStatus.FAILED, "Fiona", Map.of("failureReason", "Customer unavailable"));

        assertFalse(email.htmlBody().contains("${"));
        assertTrue(email.htmlBody().contains("SD-1006"));
        assertTrue(email.htmlBody().contains("Customer unavailable"));
        assertTrue(email.htmlBody().contains("re-attempt"));
        assertTrue(email.htmlBody().contains("http://localhost:5173/track/SD-1006"));
    }
}
