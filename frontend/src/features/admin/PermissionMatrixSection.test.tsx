import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PermissionMatrix } from '../../types/api';
import { PermissionMatrixSection, prettifyCode } from './PermissionMatrixSection';

const mutate = vi.fn();

const matrix: PermissionMatrix = {
  permissions: [
    { code: 'PATIENTS_READ', description: 'View patient records', category: 'Patients' },
    { code: 'PATIENTS_WRITE', description: 'Edit patient records', category: 'Patients' },
    { code: 'PERMISSIONS_MANAGE', description: 'Manage role permissions', category: 'Admin' },
    { code: 'USERS_MANAGE', description: 'Manage user accounts', category: 'Admin' },
  ],
  roles: {
    ADMIN: ['PATIENTS_READ', 'PATIENTS_WRITE', 'PERMISSIONS_MANAGE', 'USERS_MANAGE'],
    DENTIST: ['PATIENTS_READ'],
    HYGIENIST: ['PATIENTS_READ'],
    FRONT_DESK: ['PATIENTS_READ', 'PATIENTS_WRITE'],
    BILLING: [],
    READ_ONLY: [],
  },
};

vi.mock('./api', () => ({
  usePermissionMatrix: () => ({ data: matrix, isPending: false, isError: false }),
  useUpdateRolePermissions: () => ({ mutate, isPending: false }),
}));

describe('PermissionMatrixSection', () => {
  beforeEach(() => {
    mutate.mockReset();
  });

  it('shows no Save buttons until a role is dirty', () => {
    render(<PermissionMatrixSection />);
    expect(screen.queryByRole('button', { name: /^Save/ })).not.toBeInTheDocument();
    expect(
      screen.getByText('Changes apply immediately to active sessions.'),
    ).toBeInTheDocument();
  });

  it('toggling a permission enables Save for that role only, sending the full code list', async () => {
    render(<PermissionMatrixSection />);
    await userEvent.click(screen.getByRole('checkbox', { name: 'PATIENTS_READ FRONT_DESK' }));

    const save = screen.getByRole('button', { name: 'Save FRONT_DESK' });
    expect(screen.queryByRole('button', { name: 'Save BILLING' })).not.toBeInTheDocument();

    await userEvent.click(save);
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      role: 'FRONT_DESK',
      permissionCodes: ['PATIENTS_WRITE'],
    });
  });

  it('granting a permission includes it in the saved payload', async () => {
    render(<PermissionMatrixSection />);
    await userEvent.click(screen.getByRole('checkbox', { name: 'PATIENTS_READ BILLING' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save BILLING' }));

    expect(mutate.mock.calls[0][0]).toEqual({
      role: 'BILLING',
      permissionCodes: ['PATIENTS_READ'],
    });
  });

  it('toggling back to the server state clears the dirty flag', async () => {
    render(<PermissionMatrixSection />);
    const checkbox = screen.getByRole('checkbox', { name: 'PATIENTS_WRITE FRONT_DESK' });
    await userEvent.click(checkbox);
    expect(screen.getByRole('button', { name: 'Save FRONT_DESK' })).toBeInTheDocument();
    await userEvent.click(checkbox);
    expect(screen.queryByRole('button', { name: 'Save FRONT_DESK' })).not.toBeInTheDocument();
  });

  it("locks ADMIN's PERMISSIONS_MANAGE and USERS_MANAGE checkboxes", () => {
    render(<PermissionMatrixSection />);
    expect(screen.getByRole('checkbox', { name: 'PERMISSIONS_MANAGE ADMIN' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'USERS_MANAGE ADMIN' })).toBeDisabled();
    // other roles stay editable
    expect(
      screen.getByRole('checkbox', { name: 'PERMISSIONS_MANAGE FRONT_DESK' }),
    ).toBeEnabled();
  });

  it('prettifies permission codes', () => {
    expect(prettifyCode('PATIENTS_READ')).toBe('Patients read');
  });
});
