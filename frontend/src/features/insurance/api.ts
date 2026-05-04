import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type {
  Claim,
  ClaimStatus,
  InsuranceCarrier,
  InsurancePlan,
  PageResponse,
  PatientCoverage,
} from '../../types/api';

// ---- carriers & plans ----

export function useCarriers(q = '') {
  return useQuery({
    queryKey: ['carriers', { q }],
    queryFn: () =>
      api<PageResponse<InsuranceCarrier>>(
        `/api/v1/insurance/carriers?q=${encodeURIComponent(q)}&size=100`,
      ),
    placeholderData: (previous) => previous,
  });
}

export function useCarrierPlans(carrierId: string | null) {
  return useQuery({
    queryKey: ['carrier-plans', carrierId],
    queryFn: () => api<InsurancePlan[]>(`/api/v1/insurance/carriers/${carrierId}/plans`),
    enabled: carrierId !== null,
  });
}

export interface CarrierInput {
  name: string;
  payerId?: string | null;
  phone?: string | null;
}

export function useCreateCarrier() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CarrierInput) =>
      api<InsuranceCarrier>('/api/v1/insurance/carriers', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['carriers'] }),
  });
}

export interface PlanInput {
  carrierId: string;
  planName: string;
  groupNumber?: string | null;
  planType: string;
  annualMax?: number | null;
  deductible?: number | null;
  coverageNotes?: string | null;
}

export function useCreatePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PlanInput) =>
      api<InsurancePlan>('/api/v1/insurance/plans', { method: 'POST', body: input }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carrier-plans'] });
      queryClient.invalidateQueries({ queryKey: ['carriers'] });
    },
  });
}

// ---- patient coverage ----

export function usePatientCoverage(patientId: string) {
  return useQuery({
    queryKey: ['patient-insurance', { patientId }],
    queryFn: () => api<PatientCoverage[]>(`/api/v1/patient-insurance?patientId=${patientId}`),
  });
}

export interface CoverageInput {
  patientId: string;
  planId: string;
  subscriberPatientId: string;
  relationshipToSubscriber: string;
  memberId: string;
  priority: string;
  effectiveDate?: string | null;
  terminationDate?: string | null;
}

export function useAddCoverage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CoverageInput) =>
      api<PatientCoverage>('/api/v1/patient-insurance', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patient-insurance'] }),
  });
}

export function useRemoveCoverage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api<void>(`/api/v1/patient-insurance/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['patient-insurance'] }),
  });
}

// ---- claims ----

export function useClaims(status: string, page: number) {
  return useQuery({
    queryKey: ['claims', { status, page }],
    queryFn: () =>
      api<PageResponse<Claim>>(
        `/api/v1/claims?status=${encodeURIComponent(status)}&page=${page}&size=25`,
      ),
    placeholderData: (previous) => previous,
  });
}

function useClaimMutation<TInput>(mutationFn: (input: TInput) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['claims'] }),
  });
}

export function useCreateClaim() {
  return useClaimMutation((input: { patientInsuranceId: string; notes?: string }) =>
    api<Claim>('/api/v1/claims', { method: 'POST', body: input }),
  );
}

export function useAddClaimLine() {
  return useClaimMutation((input: { claimId: string; procedureCodeId: string }) =>
    api<Claim>(`/api/v1/claims/${input.claimId}/procedures`, {
      method: 'POST',
      body: { procedureCodeId: input.procedureCodeId },
    }),
  );
}

export function useRemoveClaimLine() {
  return useClaimMutation((input: { claimId: string; lineId: string }) =>
    api<Claim>(`/api/v1/claims/${input.claimId}/procedures/${input.lineId}`, {
      method: 'DELETE',
    }),
  );
}

export function useRecordClaimPayment() {
  return useClaimMutation(
    (input: { claimId: string; lineId: string; paidAmount: number }) =>
      api<Claim>(`/api/v1/claims/${input.claimId}/procedures/${input.lineId}/payment`, {
        method: 'POST',
        body: { paidAmount: input.paidAmount },
      }),
  );
}

export function useUpdateClaimStatus() {
  return useClaimMutation((input: { claimId: string; status: ClaimStatus }) =>
    api<Claim>(`/api/v1/claims/${input.claimId}/status`, {
      method: 'PATCH',
      body: { status: input.status },
    }),
  );
}

export const CLAIM_NEXT_STATUSES: Record<ClaimStatus, ClaimStatus[]> = {
  DRAFT: ['SUBMITTED', 'CLOSED'],
  SUBMITTED: ['ACCEPTED', 'DENIED'],
  ACCEPTED: ['PAID', 'DENIED'],
  DENIED: ['SUBMITTED', 'CLOSED'],
  PAID: ['CLOSED'],
  CLOSED: [],
};

// ---- fee schedules, coverage rules, estimates ----

export interface FeeScheduleSummary {
  id: string;
  name: string;
  description: string | null;
  feeCount: number;
}

export interface ScheduleFee {
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  standardFee: number | null;
  fee: number;
}

export interface FeeScheduleDetail {
  id: string;
  name: string;
  description: string | null;
  fees: ScheduleFee[];
}

export interface CoverageRuleEntry {
  category: string;
  coveragePercent: number;
}

export interface EstimateLine {
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  grossFee: number;
  allowedFee: number;
  coveragePercent: number;
  deductibleApplied: number;
  insuranceEstimate: number;
  patientPortion: number;
  writeOff: number;
}

export interface EstimateResult {
  hasCoverage: boolean;
  carrierName: string | null;
  planName: string | null;
  deductible: number;
  deductibleRemaining: number;
  annualMax: number | null;
  benefitsUsed: number;
  benefitsRemaining: number | null;
  lines: EstimateLine[];
  totalInsurance: number;
  totalPatient: number;
  totalWriteOff: number;
}

export function useFeeSchedules() {
  return useQuery({
    queryKey: ['fee-schedules'],
    queryFn: () => api<FeeScheduleSummary[]>('/api/v1/insurance/fee-schedules'),
  });
}

export function useFeeSchedule(id: string | null) {
  return useQuery({
    queryKey: ['fee-schedules', id],
    queryFn: () => api<FeeScheduleDetail>(`/api/v1/insurance/fee-schedules/${id}`),
    enabled: id !== null,
  });
}

export function useCreateFeeSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; description?: string }) =>
      api<FeeScheduleSummary>('/api/v1/insurance/fee-schedules', {
        method: 'POST',
        body: input,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fee-schedules'] }),
  });
}

export function useUpsertScheduleFees(scheduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (entries: Array<{ procedureCodeId: string; fee: number }>) =>
      api<FeeScheduleDetail>(`/api/v1/insurance/fee-schedules/${scheduleId}/fees`, {
        method: 'PUT',
        body: entries,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fee-schedules'] }),
  });
}

export function usePlanCoverageRules(planId: string | null) {
  return useQuery({
    queryKey: ['coverage-rules', planId],
    queryFn: () => api<CoverageRuleEntry[]>(`/api/v1/insurance/plans/${planId}/coverage-rules`),
    enabled: planId !== null,
  });
}

export function useSavePlanCoverage(planId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: {
      feeScheduleId: string | null;
      rules: CoverageRuleEntry[];
    }) => {
      await api<void>(`/api/v1/insurance/plans/${planId}/fee-schedule`, {
        method: 'PUT',
        body: { feeScheduleId: input.feeScheduleId },
      });
      return api<CoverageRuleEntry[]>(`/api/v1/insurance/plans/${planId}/coverage-rules`, {
        method: 'PUT',
        body: input.rules,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['coverage-rules'] });
      queryClient.invalidateQueries({ queryKey: ['carrier-plans'] });
    },
  });
}

export function useBenefits(patientId: string) {
  return useQuery({
    queryKey: ['benefits', patientId],
    queryFn: () => api<EstimateResult>(`/api/v1/insurance/benefits?patientId=${patientId}`),
  });
}

export function usePlanEstimate(treatmentPlanId: string) {
  return useQuery({
    queryKey: ['plan-estimate', treatmentPlanId],
    queryFn: () =>
      api<EstimateResult>(`/api/v1/treatment-plans/${treatmentPlanId}/estimate`),
  });
}

export interface EstimateItemInput {
  procedureCodeId: string;
  grossFee: number;
}

/**
 * Ad-hoc estimate for arbitrary line items (checkout uses the completed work's fees).
 * POST because it carries a body, but it computes only — safe to model as a query.
 */
export function useAdHocEstimate(patientId: string, items: EstimateItemInput[]) {
  return useQuery({
    queryKey: ['estimate', patientId, items],
    queryFn: () =>
      api<EstimateResult>('/api/v1/insurance/estimate', {
        method: 'POST',
        body: { patientId, items },
      }),
    enabled: items.length > 0,
  });
}
