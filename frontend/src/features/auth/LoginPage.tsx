import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { useAuth } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import { makeLoginSchema, type LoginForm } from './schemas';

export function LoginPage() {
  const { t } = useTranslation('auth');
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [serverError, setServerError] = useState<string | null>(null);

  const schema = useMemo(() => makeLoginSchema(t), [t]);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: LoginForm) => {
    setServerError(null);
    try {
      await login(data.email, data.password);
      const from = (location.state as { from?: string } | null)?.from ?? '/';
      navigate(from, { replace: true });
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : t('unableToSignIn'));
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-gray-900">{t('appName')}</h1>
        <p className="mt-1 text-center text-sm text-gray-500">{t('signInToYourAccount')}</p>
        <form
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          className="mt-8 space-y-4 rounded-lg bg-white p-6 shadow"
        >
          {serverError && (
            <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
              {serverError}
            </div>
          )}
          <Input
            label={t('email')}
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register('email')}
          />
          <Input
            label={t('password')}
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" loading={isSubmitting} className="w-full">
            {t('signIn')}
          </Button>
          <p className="text-center">
            <Link to="/forgot-password" className="text-sm text-brand-600 hover:underline">
              {t('forgotYourPassword')}
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
