import { z } from 'zod';

const optional = z.string().max(255).optional().or(z.literal(''));

export const patientSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(100),
  middleName: optional,
  lastName: z.string().min(1, 'Last name is required').max(100),
  dateOfBirth: z
    .string()
    .min(1, 'Date of birth is required')
    .refine((value) => !Number.isNaN(Date.parse(value)), 'Enter a valid date')
    .refine((value) => new Date(value) < new Date(), 'Date of birth must be in the past'),
  sex: z.enum(['MALE', 'FEMALE', 'OTHER', 'UNKNOWN']),
  email: z.string().email('Enter a valid email').max(320).optional().or(z.literal('')),
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
        .min(7, 'Enter a valid phone number')
        .max(30)
        .regex(/^[0-9+()\-. ]+$/, 'Invalid phone number'),
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

export type PatientFormValues = z.infer<typeof patientSchema>;

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
