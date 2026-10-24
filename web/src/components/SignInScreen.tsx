import { Alert, Button, Center, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconBuildingStore } from '@tabler/icons-react';
import { useState, type ChangeEvent, type FormEvent } from 'react';
import { ApiError, api, describeError, type Staff } from '../api/client';

interface Props {
  onSignedIn: (staff: Staff) => void;
}

/** Start of shift. The API answers with an HttpOnly session cookie this page never sees. */
export function SignInScreen({ onSignedIn }: Props) {
  const [employeeId, setEmployeeId] = useState('');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onSignedIn(await api.login(employeeId.trim().toUpperCase(), pin));
    } catch (err) {
      setPin('');
      setError(err instanceof ApiError && err.status === 400
        ? 'Enter your employee ID and a 4 to 6 digit PIN.'
        : describeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Center mih="100dvh" p="md" bg="gray.0">
      <Paper withBorder radius="lg" p={{ base: 'lg', sm: 'xl' }} w="100%" maw={420}>
        <form onSubmit={submit}>
          <Stack gap="lg">
            <Stack gap={4} align="center">
              <Center w={48} h={48} bg="indigo.8" style={{ borderRadius: 'var(--mantine-radius-md)' }}>
                <IconBuildingStore size={26} color="white" aria-hidden />
              </Center>
              <Title order={2} mt="xs">Sign in to the register</Title>
              <Text c="dimmed" size="sm">Use your employee ID and PIN.</Text>
            </Stack>
            <TextInput
              label="Employee ID"
              size="lg"
              autoFocus
              autoComplete="username"
              value={employeeId}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setEmployeeId(e.currentTarget.value)}
              required
            />
            <PasswordInput
              label="PIN"
              size="lg"
              inputMode="numeric"
              autoComplete="current-password"
              maxLength={6}
              value={pin}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setPin(e.currentTarget.value.replace(/\D/g, ''))}
              required
            />
            {error && <Alert color="red" variant="light">{error}</Alert>}
            <Button type="submit" size="xl" loading={busy} fullWidth>
              Sign in
            </Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
