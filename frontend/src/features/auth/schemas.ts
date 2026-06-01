import { z } from 'zod';

/**
 * Zod bakes messages into the schema at creation time, so translated schemas
 * are factories taking `t`. Build them in the component with
 * `useMemo(() => makeLoginSchema(t), [t])` so they rebuild on language change.
 */
export type Translate = (key: string) => string;

export function makeLoginSchema(t: Translate) {
  return z.object({
    email: z
      .string()
      .min(1, t('auth:validation.emailRequired'))
      .email(t('auth:validation.emailInvalid')),
    password: z.string().min(1, t('auth:validation.passwordRequired')),
  });
}

export type LoginForm = z.infer<ReturnType<typeof makeLoginSchema>>;

export function makeForgotPasswordSchema(t: Translate) {
  return z.object({
    email: z
      .string()
      .min(1, t('auth:validation.emailRequired'))
      .email(t('auth:validation.emailInvalid')),
  });
}

export type ForgotPasswordForm = z.infer<ReturnType<typeof makeForgotPasswordSchema>>;

export function makePasswordRules(t: Translate) {
  return z
    .string()
    .min(12, t('auth:validation.passwordMinLength'))
    .max(128, t('auth:validation.passwordMaxLength'))
    .regex(/[A-Za-z]/, t('auth:validation.passwordNeedsLetter'))
    .regex(/\d/, t('auth:validation.passwordNeedsDigit'));
}

/**
 * @deprecated English-only static rules kept for callers not yet migrated to
 * i18n (features/users/UserFormModal). Use makePasswordRules(t) instead.
 */
export const passwordRules = z
  .string()
  .min(12, 'Password must be at least 12 characters')
  .max(128, 'Password must be at most 128 characters')
  .regex(/[A-Za-z]/, 'Password must contain a letter')
  .regex(/\d/, 'Password must contain a digit');

export function makeResetPasswordSchema(t: Translate) {
  return z
    .object({
      newPassword: makePasswordRules(t),
      confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
      message: t('auth:validation.passwordsDoNotMatch'),
      path: ['confirmPassword'],
    });
}

export type ResetPasswordForm = z.infer<ReturnType<typeof makeResetPasswordSchema>>;
