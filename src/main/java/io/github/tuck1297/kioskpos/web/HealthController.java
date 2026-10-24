package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.HealthApi;
import io.github.tuck1297.kioskpos.api.model.HealthStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements HealthApi {

    @Override
    public ResponseEntity<HealthStatus> getHealth() {
        return ResponseEntity.ok(new HealthStatus().status(HealthStatus.StatusEnum.UP));
    }
}
