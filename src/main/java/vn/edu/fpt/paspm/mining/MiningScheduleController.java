package vn.edu.fpt.paspm.mining;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/mining/schedules")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class MiningScheduleController {
    private final MiningScheduleService service;
    public MiningScheduleController(MiningScheduleService service){this.service=service;}
    public enum Frequency { WEEKLY, MONTHLY }
    public record Request(@NotNull @Positive @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer storeId,@NotNull Frequency frequency,
        @Min(1) @Max(7) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer dayOfWeek,@Min(1) @Max(31) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer dayOfMonth,
        @NotNull LocalTime localTime,@NotBlank String timeZone,@NotNull @Min(1) @Max(3660) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer lookbackDays,
        Boolean compareAlgorithms) {}
    public record View(long scheduleId,int storeId,int createdBy,int updatedBy,Frequency frequency,
        Integer dayOfWeek,Integer dayOfMonth,LocalTime localTime,String timeZone,int lookbackDays,
        boolean compareAlgorithms,boolean enabled,Instant nextFireAt,long version) {}
    public record Update(@NotNull @Valid Request schedule,@NotNull Boolean enabled,@NotNull @Min(1) Long expectedVersion) {}
    public record OccurrenceView(long occurrenceId,long scheduleId,int storeId,Instant scheduledAt,long scheduleVersion,
        long configVersion,Integer actorId,LocalDate rangeStart,LocalDate rangeEnd,LocalDate analysisDate,String timeZone,
        Boolean compareAlgorithms,Integer snapshotId,Long requestId,String status,String failureCode,int attemptCount,Instant nextRetryAt,Instant createdAt,Instant updatedAt) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request){return service.create(jwt,request);}
    @GetMapping
    public MiningContracts.Page<View> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.list(page,pageSize);}
    @GetMapping("/{id}")
    public View get(@PathVariable long id){return service.get(id);}
    @GetMapping("/{id}/occurrences")
    public MiningContracts.Page<OccurrenceView> occurrences(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.occurrences(id,page,pageSize);}
    @GetMapping("/{id}/occurrences/{occurrenceId}")
    public OccurrenceView occurrence(@PathVariable long id,@PathVariable long occurrenceId){return service.occurrence(id,occurrenceId);}
    @PutMapping("/{id}")
    public View update(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Update request){return service.update(jwt,id,request);}
}
