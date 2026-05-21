export type Role =
  | 'ADMIN'
  | 'DENTIST'
  | 'HYGIENIST'
  | 'FRONT_DESK'
  | 'BILLING'
  | 'READ_ONLY';

export const ALL_ROLES: Role[] = [
  'ADMIN',
  'DENTIST',
  'HYGIENIST',
  'FRONT_DESK',
  'BILLING',
  'READ_ONLY',
];

export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: Role[];
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'DISABLED';
  roles: Role[];
  clinicId: string | null;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type PatientStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export type Sex = 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN';
export type PhoneType = 'HOME' | 'MOBILE' | 'WORK';

export interface PatientPhone {
  type: PhoneType;
  number: string;
  primary: boolean;
}

export interface PatientSummary {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  status: PatientStatus;
  primaryPhone: string | null;
  email: string | null;
  nextRecallDate: string | null;
}

export type ContactMethod = 'EMAIL' | 'SMS' | 'PHONE' | 'MAIL';
export type SmokingStatus = 'NEVER' | 'FORMER' | 'CURRENT' | 'UNKNOWN';

export interface Patient {
  id: string;
  firstName: string;
  middleName: string | null;
  lastName: string;
  dateOfBirth: string;
  sex: Sex;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  preferredLanguage: string | null;
  status: PatientStatus;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  emergencyContactRelationship: string | null;
  notes: string | null;
  phones: PatientPhone[];
  preferredName: string | null;
  pronouns: string | null;
  employer: string | null;
  occupation: string | null;
  referralSource: string | null;
  preferredContactMethod: ContactMethod | null;
  smsConsent: boolean;
  emailConsent: boolean;
  pharmacyName: string | null;
  pharmacyPhone: string | null;
  primaryProviderId: string | null;
  primaryProviderFirstName: string | null;
  primaryProviderLastName: string | null;
  smokingStatus: SmokingStatus;
  recallIntervalMonths: number;
  nextRecallDate: string | null;
  createdAt: string;
  updatedAt: string;
  // Family billing: null = self-guaranteed
  guarantorId: string | null;
  guarantorFirstName: string | null;
  guarantorLastName: string | null;
}

export type ToothConditionType =
  | 'MISSING'
  | 'CARIES'
  | 'RESTORATION'
  | 'CROWN'
  | 'ROOT_CANAL'
  | 'IMPLANT'
  | 'BRIDGE'
  | 'VENEER'
  | 'SEALANT'
  | 'EXTRACTION_PLANNED'
  | 'FRACTURE'
  | 'WATCH'
  | 'OTHER';

export interface ToothCondition {
  id: string;
  tooth: string;
  surfaces: string | null;
  condition: ToothConditionType;
  status: 'ACTIVE' | 'RESOLVED';
  notes: string | null;
  recordedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface ChartProcedure {
  planId: string;
  planTitle: string;
  planStatus: string;
  plannedProcedureId: string;
  tooth: string;
  surface: string | null;
  procedureStatus: PlannedProcedureStatus;
  code: string | null;
  description: string | null;
}

export interface ToothChart {
  conditions: ToothCondition[];
  procedures: ChartProcedure[];
}

export type AlertType = 'ALLERGY' | 'CONDITION' | 'ALERT' | 'MEDICATION';
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export interface MedicalAlert {
  id: string;
  type: AlertType;
  description: string;
  severity: AlertSeverity;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type FamilyRelationship =
  | 'GUARANTOR'
  | 'SPOUSE'
  | 'CHILD'
  | 'PARENT'
  | 'SIBLING'
  | 'OTHER';

export interface FamilyLink {
  id: string;
  relatedPatientId: string;
  relatedPatientFirstName: string;
  relatedPatientLastName: string;
  relationship: FamilyRelationship;
}

export interface TimelineEvent {
  id: string;
  action: string;
  userId: string | null;
  previousValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  occurredAt: string;
}

export type ProviderType = 'DENTIST' | 'HYGIENIST' | 'ASSISTANT';

export interface Provider {
  id: string;
  type: ProviderType;
  firstName: string;
  lastName: string;
  npi: string | null;
  specialty: string | null;
  licenseNumber: string | null;
  licenseState: string | null;
  email: string | null;
  phone: string | null;
  color: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AppointmentStatus =
  | 'SCHEDULED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'CANCELLED';

export interface Appointment {
  id: string;
  patientId: string;
  patientFirstName: string | null;
  patientLastName: string | null;
  providerId: string;
  providerFirstName: string | null;
  providerLastName: string | null;
  operatoryId: string;
  operatoryName: string | null;
  startsAt: string;
  endsAt: string;
  status: AppointmentStatus;
  asap: boolean;
  notes: string | null;
  color: string;
  cancelledReason: string | null;
  procedures: AppointmentProcedure[];
  createdAt: string;
  updatedAt: string;
}

export interface Operatory {
  id: string;
  name: string;
  active: boolean;
}

export interface AppointmentProcedure {
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  standardFee: number | null;
}

export type ProcedureCategory =
  | 'DIAGNOSTIC'
  | 'PREVENTIVE'
  | 'RESTORATIVE'
  | 'ENDODONTICS'
  | 'PERIODONTICS'
  | 'PROSTHODONTICS'
  | 'ORAL_SURGERY'
  | 'ORTHODONTICS'
  | 'ADJUNCTIVE'
  | 'OTHER';

export const PROCEDURE_CATEGORIES: ProcedureCategory[] = [
  'DIAGNOSTIC',
  'PREVENTIVE',
  'RESTORATIVE',
  'ENDODONTICS',
  'PERIODONTICS',
  'PROSTHODONTICS',
  'ORAL_SURGERY',
  'ORTHODONTICS',
  'ADJUNCTIVE',
  'OTHER',
];

export interface ProcedureCode {
  id: string;
  code: string;
  description: string;
  category: ProcedureCategory;
  standardFee: number;
  cdtCode: string | null;
  active: boolean;
}

export type TreatmentPlanStatus =
  | 'DRAFT'
  | 'PRESENTED'
  | 'APPROVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export type PlannedProcedureStatus = 'PLANNED' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface PlannedProcedure {
  id: string;
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  tooth: string | null;
  surface: string | null;
  priority: number;
  status: PlannedProcedureStatus;
  estimatedCost: number;
  completedAt: string | null;
}

export interface TreatmentPlan {
  id: string;
  patientId: string;
  providerId: string;
  providerFirstName: string | null;
  providerLastName: string | null;
  title: string;
  status: TreatmentPlanStatus;
  notes: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  totalEstimatedCost: number;
  completedCost: number;
  procedureCount: number;
  completedCount: number;
  procedures: PlannedProcedure[];
  createdAt: string;
  updatedAt: string;
}

export interface TreatmentPlanSummary {
  id: string;
  title: string;
  status: TreatmentPlanStatus;
  totalEstimatedCost: number;
  procedureCount: number;
  completedCount: number;
  createdAt: string;
}

export type ClinicalNoteType = 'EXAM' | 'PROGRESS' | 'PROCEDURE' | 'PHONE' | 'OTHER';

export interface ClinicalNote {
  id: string;
  patientId: string;
  providerId: string | null;
  appointmentId: string | null;
  authorUserId: string;
  noteType: ClinicalNoteType;
  body: string;
  signedAt: string | null;
  signedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export type InsurancePlanType = 'PPO' | 'HMO' | 'INDEMNITY' | 'MEDICAID' | 'DISCOUNT' | 'OTHER';

export interface InsuranceCarrier {
  id: string;
  name: string;
  payerId: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  planCount: number;
}

export interface InsurancePlan {
  id: string;
  carrierId: string;
  carrierName: string;
  planName: string;
  groupNumber: string | null;
  planType: InsurancePlanType;
  annualMax: number | null;
  deductible: number | null;
  coverageNotes: string | null;
  feeScheduleId: string | null;
}

export type CoverageRelationship = 'SELF' | 'SPOUSE' | 'CHILD' | 'OTHER';
export type CoveragePriority = 'PRIMARY' | 'SECONDARY';

export interface PatientCoverage {
  id: string;
  patientId: string;
  planId: string;
  planName: string;
  planType: InsurancePlanType;
  carrierName: string;
  subscriberPatientId: string;
  subscriberFirstName: string | null;
  subscriberLastName: string | null;
  relationshipToSubscriber: CoverageRelationship;
  memberId: string;
  priority: CoveragePriority;
  effectiveDate: string | null;
  terminationDate: string | null;
}

export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'DENIED' | 'PAID' | 'CLOSED';

export interface ClaimLine {
  id: string;
  procedureCodeId: string;
  code: string | null;
  description: string | null;
  billedAmount: number;
  paidAmount: number;
}

export interface Claim {
  id: string;
  patientInsuranceId: string;
  patientId: string;
  patientFirstName: string | null;
  patientLastName: string | null;
  carrierName: string | null;
  planName: string | null;
  memberId: string | null;
  status: ClaimStatus;
  submittedAt: string | null;
  notes: string | null;
  totalBilled: number;
  totalPaid: number;
  procedures: ClaimLine[];
  createdAt: string;
  updatedAt: string;
  // Coordination of benefits: set when this claim is/has a secondary claim
  parentClaimId: string | null;
  secondaryClaimId: string | null;
}

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

// ---- Phase D: patient forms, e-signature & clinical note templates ----

export type FormFieldType = 'TEXT' | 'TEXTAREA' | 'CHECKBOX' | 'DATE' | 'SELECT';

export interface FormField {
  key: string;
  label: string;
  type: FormFieldType;
  required: boolean;
  options?: string[];
}

export interface FormTemplate {
  id: string;
  name: string;
  description: string | null;
  fields: FormField[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type FormInstanceStatus = 'DRAFT' | 'COMPLETED' | 'SIGNED';

/** CHECKBOX answers are booleans; every other field type stores a string. */
export type FormAnswerValue = string | boolean;

export interface FormInstance {
  id: string;
  templateId: string;
  templateName: string;
  patientId: string;
  status: FormInstanceStatus;
  answers: Record<string, FormAnswerValue>;
  signedAt: string | null;
  signedByName: string | null;
  documentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface NoteTemplate {
  id: string;
  name: string;
  noteType: ClinicalNoteType;
  body: string;
  /** Placeholder keys extracted server-side from {{...}} markers in the body. */
  prompts: string[];
  createdAt: string;
  updatedAt: string;
}
