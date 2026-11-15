package lk.ac.mrt.swiftdrop.gateway.dashboard;

import reactor.core.publisher.Mono;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter.REQUEST_ID_HEADER;

@RestController
@RequestMapping("/api/v1/dashboard")
public final class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/summary")
	Mono<DashboardResponse> getDashboard(
			JwtAuthenticationToken authentication,
			@RequestHeader(REQUEST_ID_HEADER) String requestId) {

		return dashboardService.getDashboard(
				"Bearer " + authentication.getToken().getTokenValue(),
				requestId);
	}
}
