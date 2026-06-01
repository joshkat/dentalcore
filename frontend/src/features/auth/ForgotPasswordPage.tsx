import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { api } from '../../lib/api';
import { makeForgotPasswordSchema, type ForgotPasswordForm } from './schemas';

export function ForgotPasswordPage() {
  const { t } = useTranslation('auth');
  const [sent, setSent] = useState(false);

  const schema = useMemo(() => makeForgotPasswordSchema(t), [t]);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordForm>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: ForgotPasswordForm) => {
    await api<void>('/api/v1/auth/forgot-password', { method: 'POST', body: data });
    setSent(true);
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-gray-900">{t('resetYourPassword')}</h1>
        <div className="mt-8 rounded-lg bg-white p-6 shadow">
          {sent ? (
            <p className="text-sm text-gray-700">{t('resetLinkSent')}</p>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
              <Input
                label={t('email')}
                type="email"
                autoComplete="email"
                error={errors.email?.message}
                {...register('email')}
              />
              <Button type="submit" loading={isSubmitting} className="w-full">
                {t('sendResetLink')}
              </Button>
            </form>
          )}
          <p className="mt-4 text-center">
            <Link to="/login" className="text-sm text-brand-600 hover:underline">
              {t('backToSignIn')}
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
