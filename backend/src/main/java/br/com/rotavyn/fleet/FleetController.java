package br.com.rotavyn.fleet;

import br.com.rotavyn.identity.DemoTenants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FleetController {
    private final JdbcTemplate jdbc;
    private final DemoTenants tenants;

    public FleetController(JdbcTemplate jdbc, DemoTenants tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public record Driver(UUID id, String displayName) {}
    public record Vehicle(UUID id, String plate) {}
    public record NewDriver(@NotBlank @Size(max = 160) String displayName) {}
    public record NewVehicle(@NotBlank @Size(max = 32) String plate) {}

    @PostMapping("/drivers")
    public ResponseEntity<Driver> createDriver(@Valid @RequestBody NewDriver request, Authentication auth) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO driver(id,tenant_id,display_name) VALUES (?,?,?)", id,
            tenants.tenantFor(auth.getName()), request.displayName());
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + id)).body(new Driver(id, request.displayName()));
    }

    @GetMapping("/drivers")
    public List<Driver> drivers(Authentication auth) {
        return jdbc.query("SELECT id,display_name FROM driver WHERE tenant_id=? ORDER BY display_name,id",
            (rs, row) -> new Driver(rs.getObject("id", UUID.class), rs.getString("display_name")), tenants.tenantFor(auth.getName()));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<Vehicle> createVehicle(@Valid @RequestBody NewVehicle request, Authentication auth) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO vehicle(id,tenant_id,plate) VALUES (?,?,?)", id,
            tenants.tenantFor(auth.getName()), request.plate());
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + id)).body(new Vehicle(id, request.plate()));
    }

    @GetMapping("/vehicles")
    public List<Vehicle> vehicles(Authentication auth) {
        return jdbc.query("SELECT id,plate FROM vehicle WHERE tenant_id=? ORDER BY plate,id",
            (rs, row) -> new Vehicle(rs.getObject("id", UUID.class), rs.getString("plate")), tenants.tenantFor(auth.getName()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<String> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Vehicle plate already exists for this tenant");
    }
}
