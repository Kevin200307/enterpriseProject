package lk.ac.mrt.swiftdrop.notification_service.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class NotificationLogSpecificationsTest {

    @Test
    @DisplayName("Should build predicate with all filters provided")
    void shouldBuildPredicateWithAllFilters() {
        Root<NotificationLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> statusPath = mock(Path.class);
        Path<Object> channelPath = mock(Path.class);
        Path<Instant> createdAtPath = mock(Path.class);

        when(root.get("status")).thenReturn(statusPath);
        when(root.get("channel")).thenReturn(channelPath);
        when(root.<Instant>get("createdAt")).thenReturn(createdAtPath);

        Predicate dummyPredicate = mock(Predicate.class);
        when(cb.equal(any(), any())).thenReturn(dummyPredicate);
        when(cb.greaterThanOrEqualTo(any(), any(Instant.class))).thenReturn(dummyPredicate);
        when(cb.lessThanOrEqualTo(any(), any(Instant.class))).thenReturn(dummyPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(dummyPredicate);

        Instant from = Instant.now().minusSeconds(100);
        Instant to = Instant.now();

        Specification<NotificationLog> spec = NotificationLogSpecifications.withFilters(
                NotificationStatus.SENT, NotificationChannel.EMAIL, from, to);

        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb, times(1)).equal(statusPath, NotificationStatus.SENT);
        verify(cb, times(1)).equal(channelPath, NotificationChannel.EMAIL);
        verify(cb, times(1)).greaterThanOrEqualTo(createdAtPath, from);
        verify(cb, times(1)).lessThanOrEqualTo(createdAtPath, to);
    }

    @Test
    @DisplayName("Should build empty predicate when all filters are null")
    void shouldBuildEmptyPredicateWhenFiltersNull() {
        Root<NotificationLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate dummyPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(dummyPredicate);

        Specification<NotificationLog> spec = NotificationLogSpecifications.withFilters(null, null, null, null);

        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb, never()).equal(any(), any());
        verify(cb, never()).greaterThanOrEqualTo(any(), any(Instant.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(Instant.class));
    }
}
