package lk.ac.mrt.swiftdrop.dispatch_service.api;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Checked-in OpenAPI contract, served through the gateway's dispatch documentation route. */
@RestController
public class OpenApiController {
    @GetMapping(value="/v3/api-docs",produces=MediaType.APPLICATION_JSON_VALUE)
    public Resource document() { return new ClassPathResource("openapi.json"); }
}
