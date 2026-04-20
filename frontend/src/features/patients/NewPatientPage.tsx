import { useNavigate } from 'react-router-dom';
import { useCreatePatient } from './api';
import { PatientForm } from './PatientForm';
import { emptyPatient } from './schemas';

export function NewPatientPage() {
  const navigate = useNavigate();
  const createPatient = useCreatePatient();

  return (
    <div className="mx-auto max-w-4xl p-8">
      <h1 className="text-2xl font-bold text-gray-900">New patient</h1>
      <div className="mt-6 rounded-lg bg-white p-6 shadow">
        <PatientForm
          defaultValues={emptyPatient}
          submitLabel="Register patient"
          onCancel={() => navigate('/patients')}
          onSubmit={async (values) => {
            const patient = await createPatient.mutateAsync(values);
            navigate(`/patients/${patient.id}`);
          }}
        />
      </div>
    </div>
  );
}
