import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export type LedgerEntryType = 'CHARGE' | 'PAYMENT' | 'ADJUSTMENT' | 'INSURANCE_PAYMENT';

export interface LedgerEntry {
  id: string;
  type: LedgerEntryType;
  amount: number;
  description: string;
  method: string | null;
  procedureCodeId: string | null;
  procedureCode: string | null;
  appointmentId: string | null;
  claimId: string | null;
  entryDate: string;
  reversalOf: string | null;
  reversed: boolean;
  createdAt: string;
}

export interface LedgerResponse {
  balance: number;
  content: LedgerEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function useLedger(patientId: string, page = 0) {
  return useQuery({
    queryKey: ['ledger', { patientId, page }],
    queryFn: () =>
      api<LedgerResponse>(`/api/v1/billing/ledger?patientId=${patientId}&page=${page}&size=50`),
    placeholderData: (previous) => previous,
  });
}

export function useBalance(patientId: string, enabled = true) {
  return useQuery({
    queryKey: ['balance', patientId],
    queryFn: () => api<{ balance: number }>(`/api/v1/billing/balance?patientId=${patientId}`),
    enabled,
  });
}

function useLedgerMutation<TInput>(mutationFn: (input: TInput) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ledger'] });
      queryClient.invalidateQueries({ queryKey: ['balance'] });
      queryClient.invalidateQueries({ queryKey: ['family-ledger'] });
    },
  });
}

export function usePostCharge() {
  return useLedgerMutation(
    (input: {
      patientId: string;
      procedureCodeId?: string;
      amount?: number;
      description?: string;
    }) => api<LedgerEntry>('/api/v1/billing/charges', { method: 'POST', body: input }),
  );
}

export function useRecordPayment() {
  return useLedgerMutation(
    (input: { patientId: string; amount: number; method: string; description?: string }) =>
      api<LedgerEntry>('/api/v1/billing/payments', { method: 'POST', body: input }),
  );
}

export function usePostAdjustment() {
  return useLedgerMutation(
    (input: { patientId: string; amount: number; description: string }) =>
      api<LedgerEntry>('/api/v1/billing/adjustments', { method: 'POST', body: input }),
  );
}

export function useReverseEntry() {
  return useLedgerMutation((input: { entryId: string; reason: string }) =>
    api<LedgerEntry>(`/api/v1/billing/entries/${input.entryId}/reverse`, {
      method: 'POST',
      body: { reason: input.reason },
    }),
  );
}

/** Walk-out statement for an appointment — opens the PDF in a new tab. */
export async function openWalkout(appointmentId: string): Promise<void> {
  const { getAccessToken, refreshSession } = await import('../../lib/api');
  const attempt = () =>
    fetch(`/api/v1/billing/walkout?appointmentId=${appointmentId}`, {
      headers: getAccessToken()
        ? { Authorization: `Bearer ${getAccessToken()}` }
        : undefined,
      credentials: 'include',
    });
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    throw new Error(`Walk-out statement failed (${response.status})`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  // Give the new tab time to load the blob before revoking it.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export async function downloadStatement(patientId: string): Promise<void> {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 90);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  const { getAccessToken, refreshSession } = await import('../../lib/api');
  const attempt = () =>
    fetch(
      `/api/v1/billing/statement?patientId=${patientId}&from=${iso(from)}&to=${iso(to)}`,
      {
        headers: getAccessToken()
          ? { Authorization: `Bearer ${getAccessToken()}` }
          : undefined,
        credentials: 'include',
      },
    );
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    throw new Error(`Statement failed (${response.status})`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `statement-${iso(from)}-${iso(to)}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

// ---- family ledger (guarantor view) ----

export interface FamilyMemberBalance {
  patientId: string;
  patientName: string;
  balance: number;
}

export interface FamilyLedgerEntry extends LedgerEntry {
  patientId: string;
  patientName: string;
}

export interface FamilyLedgerResponse {
  guarantorId: string;
  guarantorName: string;
  members: FamilyMemberBalance[];
  entries: FamilyLedgerEntry[];
  totalBalance: number;
}

export function useFamilyLedger(guarantorId: string, enabled = true) {
  return useQuery({
    queryKey: ['family-ledger', guarantorId],
    queryFn: () =>
      api<FamilyLedgerResponse>(`/api/v1/billing/family-ledger?guarantorId=${guarantorId}`),
    enabled,
  });
}

export async function downloadFamilyStatement(guarantorId: string): Promise<void> {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 90);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  const { getAccessToken, refreshSession } = await import('../../lib/api');
  const attempt = () =>
    fetch(
      `/api/v1/billing/family-statement?guarantorId=${guarantorId}&from=${iso(from)}&to=${iso(to)}`,
      {
        headers: getAccessToken()
          ? { Authorization: `Bearer ${getAccessToken()}` }
          : undefined,
        credentials: 'include',
      },
    );
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    throw new Error(`Family statement failed (${response.status})`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `family-statement-${iso(from)}-${iso(to)}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

// ---- payment plans ----

export type PaymentPlanFrequency = 'MONTHLY' | 'BIWEEKLY';
export type PaymentPlanStatus = 'ACTIVE' | 'COMPLETED' | 'DEFAULTED' | 'CANCELLED';

export interface PaymentPlanInstallment {
  dueDate: string;
  amount: number;
}

export interface PaymentPlan {
  id: string;
  patientId: string;
  totalAmount: number;
  downPayment: number;
  installmentAmount: number;
  frequency: PaymentPlanFrequency;
  firstDueDate: string;
  status: PaymentPlanStatus;
  notes: string | null;
  installments: PaymentPlanInstallment[];
  expectedToDate: number;
  receivedToDate: number;
  createdAt: string;
}

export interface PaymentPlanInput {
  patientId: string;
  totalAmount: number;
  downPayment?: number;
  installmentAmount: number;
  frequency: PaymentPlanFrequency;
  firstDueDate: string;
  notes?: string;
}

export function usePaymentPlans(patientId: string) {
  return useQuery({
    queryKey: ['payment-plans', patientId],
    queryFn: () => api<PaymentPlan[]>(`/api/v1/billing/payment-plans?patientId=${patientId}`),
  });
}

export function useCreatePaymentPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PaymentPlanInput) =>
      api<PaymentPlan>('/api/v1/billing/payment-plans', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payment-plans'] }),
  });
}

export function useUpdatePaymentPlanStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { planId: string; status: PaymentPlanStatus }) =>
      api<PaymentPlan>(`/api/v1/billing/payment-plans/${input.planId}/status`, {
        method: 'PATCH',
        body: { status: input.status },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payment-plans'] }),
  });
}
