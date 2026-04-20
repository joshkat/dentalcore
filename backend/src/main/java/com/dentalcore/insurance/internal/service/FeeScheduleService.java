package com.dentalcore.insurance.internal.service;

import com.dentalcore.insurance.internal.entity.CoverageRule;
import com.dentalcore.insurance.internal.entity.FeeSchedule;
import com.dentalcore.insurance.internal.entity.InsurancePlan;
import com.dentalcore.insurance.internal.repository.CoverageRuleRepository;
import com.dentalcore.insurance.internal.repository.FeeScheduleRepository;
import com.dentalcore.procedures.api.ProcedureCatalogApi;
import com.dentalcore.procedures.api.ProcedureSummary;
import com.dentalcore.shared.error.ConflictException;
import com.dentalcore.shared.error.InvalidRequestException;
import com.dentalcore.shared.error.ResourceNotFoundException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FeeScheduleService {

    private final FeeScheduleRepository scheduleRepository;
    private final CoverageRuleRepository coverageRuleRepository;
    private final InsuranceAdminService adminService;
    private final ProcedureCatalogApi catalogApi;

    public FeeScheduleService(FeeScheduleRepository scheduleRepository,
                              CoverageRuleRepository coverageRuleRepository,
                              InsuranceAdminService adminService,
                              ProcedureCatalogApi catalogApi) {
        this.scheduleRepository = scheduleRepository;
        this.coverageRuleRepository = coverageRuleRepository;
        this.adminService = adminService;
        this.catalogApi = catalogApi;
    }

    public record ScheduleRequest(@NotBlank @Size(max = 200) String name,
                                  @Size(max = 500) String description) {
    }

    public record FeeEntry(@NotNull UUID procedureCodeId,
                           @NotNull @DecimalMin("0.00") BigDecimal fee) {
    }

    public record FeeResponse(UUID procedureCodeId, String code, String description,
                              BigDecimal standardFee, BigDecimal fee) {
    }

    public record ScheduleResponse(UUID id, String name, String description, int feeCount) {
    }

    public record ScheduleDetailResponse(UUID id, String name, String description,
                                         List<FeeResponse> fees) {
    }

    public record CoverageRuleEntry(
            @NotNull
            @Pattern(regexp = "DIAGNOSTIC|PREVENTIVE|RESTORATIVE|ENDODONTICS|PERIODONTICS"
                    + "|PROSTHODONTICS|ORAL_SURGERY|ORTHODONTICS|ADJUNCTIVE|OTHER")
            String category,
            @NotNull @Min(0) @Max(100) Integer coveragePercent) {
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list() {
        return scheduleRepository.findAllByOrderByName().stream()
                .map(s -> new ScheduleResponse(s.getId(), s.getName(), s.getDescription(),
                        s.getFees().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDetailResponse get(UUID id) {
        return toDetail(findSchedule(id));
    }

    public ScheduleResponse create(ScheduleRequest request) {
        if (scheduleRepository.findAllByOrderByName().stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(request.name()))) {
            throw new ConflictException("A fee schedule with this name already exists");
        }
        FeeSchedule schedule = scheduleRepository.save(
                new FeeSchedule(request.name(), request.description()));
        return new ScheduleResponse(schedule.getId(), schedule.getName(),
                schedule.getDescription(), 0);
    }

    public ScheduleDetailResponse upsertFees(UUID id, List<FeeEntry> entries) {
        FeeSchedule schedule = findSchedule(id);
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                entries.stream().map(FeeEntry::procedureCodeId).collect(Collectors.toSet()));
        for (FeeEntry entry : entries) {
            if (!catalog.containsKey(entry.procedureCodeId())) {
                throw new InvalidRequestException(
                        "Unknown procedure code: " + entry.procedureCodeId());
            }
            schedule.upsertFee(entry.procedureCodeId(), entry.fee());
        }
        return toDetail(schedule);
    }

    public ScheduleDetailResponse removeFee(UUID id, UUID procedureCodeId) {
        FeeSchedule schedule = findSchedule(id);
        schedule.removeFee(procedureCodeId);
        return toDetail(schedule);
    }

    // ---- plan links + coverage rules ----

    public void linkPlanToSchedule(UUID planId, UUID feeScheduleId) {
        InsurancePlan plan = adminService.requirePlan(planId);
        if (feeScheduleId != null) {
            findSchedule(feeScheduleId);
        }
        plan.linkFeeSchedule(feeScheduleId);
    }

    @Transactional(readOnly = true)
    public List<CoverageRuleEntry> coverageRules(UUID planId) {
        adminService.requirePlan(planId);
        return coverageRuleRepository.findByPlanId(planId).stream()
                .map(rule -> new CoverageRuleEntry(rule.getCategory(),
                        rule.getCoveragePercent()))
                .toList();
    }

    public List<CoverageRuleEntry> replaceCoverageRules(UUID planId,
                                                        List<CoverageRuleEntry> entries) {
        adminService.requirePlan(planId);
        Set<String> seen = entries.stream().map(CoverageRuleEntry::category)
                .collect(Collectors.toSet());
        if (seen.size() != entries.size()) {
            throw new InvalidRequestException("Duplicate category in coverage rules");
        }
        coverageRuleRepository.deleteAll(coverageRuleRepository.findByPlanId(planId));
        coverageRuleRepository.saveAll(entries.stream()
                .map(entry -> new CoverageRule(planId, entry.category(),
                        entry.coveragePercent()))
                .toList());
        return coverageRules(planId);
    }

    // ---- helpers ----

    FeeSchedule findSchedule(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee schedule", id));
    }

    private ScheduleDetailResponse toDetail(FeeSchedule schedule) {
        Map<UUID, ProcedureSummary> catalog = catalogApi.findSummaries(
                schedule.getFees().stream()
                        .map(f -> f.getProcedureCodeId())
                        .collect(Collectors.toSet()));
        List<FeeResponse> fees = schedule.getFees().stream()
                .map(f -> {
                    ProcedureSummary entry = catalog.get(f.getProcedureCodeId());
                    return new FeeResponse(f.getProcedureCodeId(),
                            entry != null ? entry.code() : null,
                            entry != null ? entry.description() : null,
                            entry != null ? entry.standardFee() : null,
                            f.getFee());
                })
                .sorted((a, b) -> String.valueOf(a.code()).compareTo(String.valueOf(b.code())))
                .toList();
        return new ScheduleDetailResponse(schedule.getId(), schedule.getName(),
                schedule.getDescription(), fees);
    }
}
