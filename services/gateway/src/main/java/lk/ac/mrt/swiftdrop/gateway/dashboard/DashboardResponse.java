package lk.ac.mrt.swiftdrop.gateway.dashboard;

import tools.jackson.databind.JsonNode;

/**
 * Bounded dashboard view. An unavailable section does not hide healthy data
 * returned by the other services.
 */
public record DashboardResponse(
		Section parcels,
		Section dispatch,
		Section merchants) {

	public record Section(boolean available, JsonNode data) {

		static Section available(JsonNode data) {
			return new Section(true, data);
		}

		static Section unavailable() {
			return new Section(false, null);
		}
	}
}
