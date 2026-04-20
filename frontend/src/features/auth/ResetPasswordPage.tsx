import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Input } from '../../components/Input';
import { api, ApiError } from '../../lib/api';
import { resetPasswordSchema, type ResetPasswordForm } from './schemas';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token');
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordForm>({ resolver: zodResolver(resetPasswordSchema) });

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <div className="rounded-lg bg-white p-6 text-sm text-gray-700 shadow">
          This reset link is invalid.{' '}
          <Link to="/forgot-password" className="text-brand-600 hover:underline">
            Request a new one.
          </Link>
        </div>
      </div>
    );
  }

  const onSubmit = async (data: ResetPasswordForm) => {
    setServerError(null);
    try {
      await api<void>('/api/v1/auth/reset-password', {
        method: 'POST',
        body: { token, newPassword: data.newPassword },
      });
      navigate('/login', { replace: true });
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? 'This reset link is invalid or has expired.'
          : 'Something went wrong. Try again.',
      );
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-gray-900">Choose a new password</h1>
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
            label="New password"
            type="password"
            autoComplete="new-password"
            error={errors.newPassword?.message}
            {...register('newPassword')}
          />
          <Input
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />
          <Button type="submit" loading={isSubmitting} className="w-full">
            Set new password
          </Button>
        </form>
      </div>
    </div>
  );
}
