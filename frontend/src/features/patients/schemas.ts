import { z } from 'zod';

/**
 * Zod bakes messages into the schema at creation time, so translated schemas
 * are factories taking `t` (see i18n/GUIDE.md). Build them in the component
 * with `useMemo(() => makePatientSchema(t), [t])` so they rebuild on language
 * change.
 */
export type Translate = (key: string) => string;

const optional = z.string().max(255).optional().or(z.literal(''));

export function makePatientSchema(t: Translate) {
  return z.object({
    firstName: z.string().min(1, t('patients:validation.firstNameRequired')).max(100),
    middleName: optional,
    lastName: z.string().min(1, t('patients:validation.lastNameRequired')).max(100),
    dateOfBirth: z
      .string()
      .min(1, t('patients:validation.dobRequired'))
      .refine((value) => !Number.isNaN(Date.parse(value)), t('patients:validation.dobInvalid'))
      .refine((value) => new Date(value) < new Date(), t('patients:validation.dobPast')),
    sex: z.enum(['MALE', 'FEMALE', 'OTHER', 'UNKNOWN']),
    email: z
      .string()
      .email(t('patients:validation.emailInvalid'))
      .max(320)
      .optional()
      .or(z.literal('')),
    addressLine1: optional,
    addressLine2: optional,
    city: optional,
    state: optional,
    postalCode: optional,
    preferredLanguage: optional,
    emergencyContactName: optional,
    emergencyContactPhone: optional,
    emergencyContactRelationship: optional,
    notes: z.string().max(10_000).optional().or(z.literal('')),
    phones: z.array(
      z.object({
        type: z.enum(['HOME', 'MOBILE', 'WORK']),
        number: z
          .string()
          .min(7, t('patients:validation.phoneMin'))
          .max(30)
          .regex(/^[0-9+()\-. ]+$/, t('patients:validation.phoneInvalid')),
        primary: z.boolean(),
      }),
    ),
    preferredName: z.string().max(100).optional().or(z.literal('')),
    pronouns: z.string().max(30).optional().or(z.literal('')),
    employer: z.string().max(200).optional().or(z.literal('')),
    occupation: z.string().max(100).optional().or(z.literal('')),
    referralSource: z.string().max(200).optional().or(z.literal('')),
    preferredContactMethod: z.enum(['', 'EMAIL', 'SMS', 'PHONE', 'MAIL']),
    smsConsent: z.boolean(),
    emailConsent: z.boolean(),
    pharmacyName: z.string().max(200).optional().or(z.literal('')),
    pharmacyPhone: z.string().max(30).optional().or(z.literal('')),
    primaryProviderId: z.string(),
    smokingStatus: z.enum(['NEVER', 'FORMER', 'CURRENT', 'UNKNOWN']),
  });
}

export type PatientFormValues = z.infer<ReturnType<typeof makePatientSchema>>;

export const emptyPatient: PatientFormValues = {
  firstName: '',
  middleName: '',
  lastName: '',
  dateOfBirth: '',
  sex: 'UNKNOWN',
  email: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  preferredLanguage: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  emergencyContactRelationship: '',
  notes: '',
  phones: [{ type: 'MOBILE', number: '', primary: true }],
  preferredName: '',
  pronouns: '',
  employer: '',
  occupation: '',
  referralSource: '',
  preferredContactMethod: '',
  smsConsent: false,
  emailConsent: false,
  pharmacyName: '',
  pharmacyPhone: '',
  primaryProviderId: '',
  smokingStatus: 'UNKNOWN',
};
