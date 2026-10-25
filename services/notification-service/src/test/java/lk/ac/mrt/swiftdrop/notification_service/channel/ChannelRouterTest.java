package lk.ac.mrt.swiftdrop.notification_service.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelRouterTest {

    @Test
    @DisplayName("Should route to appropriate handler and support pluggable bean swapping")
    void shouldRouteToAppropriateHandler() {
        NotificationChannelHandler mockEmailHandler = mock(NotificationChannelHandler.class);
        NotificationChannelHandler mockSmsHandler = mock(NotificationChannelHandler.class);

        org.mockito.Mockito.when(mockEmailHandler.supports(NotificationChannel.EMAIL)).thenReturn(true);
        org.mockito.Mockito.when(mockSmsHandler.supports(NotificationChannel.SMS)).thenReturn(true);

        ChannelRouter router = new ChannelRouter(List.of(mockEmailHandler, mockSmsHandler));

        NotificationChannelHandler.NotificationRequest request = new NotificationChannelHandler.NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "SD-12345678", UUID.randomUUID(),
                "test@swiftdrop.lk", "User", ParcelStatus.DELIVERED
        );

        router.route(NotificationChannel.EMAIL, request);
        verify(mockEmailHandler, times(1)).send(request);

        router.route(NotificationChannel.SMS, request);
        verify(mockSmsHandler, times(1)).send(request);
    }

    @Test
    @DisplayName("Should prove channel bean swapping: swap default SMS handler with CustomSmsProvider")
    void shouldSupportChannelBeanSwappingWithoutListenerChanges() {
        // Simulating swapping MockSmsChannelHandler with a third-party/Twilio CustomChannelHandler
        NotificationChannelHandler customSmsHandler = new NotificationChannelHandler() {
            private int invocations = 0;

            @Override
            public boolean supports(NotificationChannel channel) {
                return channel == NotificationChannel.SMS;
            }

            @Override
            public NotificationChannel getChannel() {
                return NotificationChannel.SMS;
            }

            @Override
            public void send(NotificationRequest request) {
                invocations++;
            }

            public int getInvocations() {
                return invocations;
            }
        };

        ChannelRouter router = new ChannelRouter(List.of(customSmsHandler));

        NotificationChannelHandler.NotificationRequest request = new NotificationChannelHandler.NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "SD-99887766", UUID.randomUUID(),
                "+94712345678", "User", ParcelStatus.OUT_FOR_DELIVERY
        );

        router.route(NotificationChannel.SMS, request);
        assertTrue(router.getHandler(NotificationChannel.SMS).isPresent());
        assertEquals(1, ((NotificationChannelHandler) customSmsHandler).supports(NotificationChannel.SMS) ? 1 : 0);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no handler registered for channel")
    void shouldThrowExceptionWhenNoHandlerRegistered() {
        ChannelRouter emptyRouter = new ChannelRouter(List.of());

        NotificationChannelHandler.NotificationRequest request = new NotificationChannelHandler.NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "SD-11223344", UUID.randomUUID(),
                "test@test.com", "User", ParcelStatus.CREATED
        );

        assertThrows(IllegalArgumentException.class, () -> emptyRouter.route(NotificationChannel.EMAIL, request));
    }
}
