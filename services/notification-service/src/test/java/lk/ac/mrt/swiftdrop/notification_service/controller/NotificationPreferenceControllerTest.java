package lk.ac.mrt.swiftdrop.notification_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationPreferenceDto;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationPreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    @Mock
    private NotificationPreferenceService preferenceService;

    @InjectMocks
    private NotificationPreferenceController preferenceController;

    @Test
    @DisplayName("GET /api/v1/notifications/preferences should return user preferences")
    void shouldReturnPreferences() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(true)
                .smsStatuses("OUT_FOR_DELIVERY,DELIVERED")
                .build();

        when(preferenceService.getPreferences(userId)).thenReturn(preference);

        ResponseEntity<NotificationPreferenceDto> response = preferenceController.getPreferences(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().userId());
        assertEquals(true, response.getBody().emailEnabled());
        assertEquals(true, response.getBody().smsEnabled());
        verify(preferenceService, times(1)).getPreferences(userId);
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/preferences should update and return preferences")
    void shouldUpdatePreferences() {
        UUID userId = UUID.randomUUID();
        NotificationPreferenceDto requestDto = new NotificationPreferenceDto(
                userId, true, false, Set.of(ParcelStatus.DELIVERED));

        NotificationPreference updated = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(false)
                .smsStatuses("DELIVERED")
                .build();

        when(preferenceService.updatePreferences(any(NotificationPreferenceDto.class))).thenReturn(updated);

        ResponseEntity<NotificationPreferenceDto> response = preferenceController.updatePreferences(requestDto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().userId());
        assertEquals(false, response.getBody().smsEnabled());
        verify(preferenceService, times(1)).updatePreferences(any(NotificationPreferenceDto.class));
    }
}
