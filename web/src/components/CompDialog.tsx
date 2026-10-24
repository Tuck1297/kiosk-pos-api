import { Alert, Button, Group, Modal, PasswordInput, Radio, Select, Stack, Text, TextInput } from '@mantine/core';
import { useRef, useState, type ChangeEvent } from 'react';
import { ApiError, api, describeError, type CompReason, type Order } from '../api/client';
import { formatMoney } from '../money';

const REASONS: { value: CompReason; label: string }[] = [
  { value: 'GUEST_RECOVERY', label: 'Guest recovery' },
  { value: 'WRONG_ORDER', label: 'Wrong order' },
  { value: 'QUALITY_ISSUE', label: 'Quality issue' },
  { value: 'EMPLOYEE_MEAL', label: 'Employee meal' },
  { value: 'OTHER', label: 'Other (add a note)' },
];

const WHOLE_ORDER = 'WHOLE_ORDER';

interface Props {
  order: Order;
  opened: boolean;
  onClose: () => void;
  onComped: (order: Order) => void;
}

/**
 * Manager approval for a comp. The manager enters their own ID and PIN on the cashier's
 * register; the server checks them, records who approved it, and re-prices the order.
 */
export function CompDialog({ order, opened, onClose, onComped }: Props) {
  const compable = order.lines.filter((l) => !l.comped);
  const [target, setTarget] = useState<string>(WHOLE_ORDER);
  const [reason, setReason] = useState<CompReason | null>(null);
  const [note, setNote] = useState('');
  const [managerId, setManagerId] = useState('');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const attemptKey = useRef<string | null>(null);

  const noteRequired = reason === 'OTHER';
  const ready = reason !== null && managerId.trim() !== '' && pin.length >= 4 && (!noteRequired || note.trim() !== '');

  function close() {
    setPin('');
    setError(null);
    onClose();
  }

  async function submit() {
    if (!reason) return;
    attemptKey.current ??= crypto.randomUUID();
    setBusy(true);
    setError(null);
    try {
      const updated = await api.comp(order, {
        orderLineId: target === WHOLE_ORDER ? undefined : target,
        reason,
        note: note.trim() || undefined,
        managerEmployeeId: managerId.trim().toUpperCase(),
        managerPin: pin,
      }, attemptKey.current);
      attemptKey.current = null;
      setPin('');
      setNote('');
      setReason(null);
      setTarget(WHOLE_ORDER);
      onComped(updated);
    } catch (e) {
      if (e instanceof ApiError) {
        attemptKey.current = null;
        setPin('');
      }
      setError(e instanceof ApiError && e.status === 403
        ? 'Manager ID or PIN not accepted. A manager must approve comps.'
        : describeError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened={opened} onClose={close} title="Comp with manager approval" size="lg" centered>
      <Stack gap="md">
        <Radio.Group label="What to comp" value={target} onChange={setTarget}>
          <Stack gap="xs" mt="xs">
            <Radio value={WHOLE_ORDER} label="Whole order (everything not already comped)" />
            {compable.map((l) => (
              <Radio key={l.id} value={l.id} label={`${l.quantity}× ${l.name} (${formatMoney(l.lineTotal)})`} />
            ))}
          </Stack>
        </Radio.Group>

        <Select
          label="Reason"
          placeholder="Choose a reason"
          data={REASONS}
          value={reason}
          onChange={(v: string | null) => setReason(v as CompReason | null)}
          allowDeselect={false}
          required
        />
        <TextInput
          label="Note"
          description={noteRequired ? 'Required for Other.' : 'Optional.'}
          maxLength={200}
          value={note}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setNote(e.currentTarget.value)}
          required={noteRequired}
        />

        <Group grow align="flex-start">
          <TextInput
            label="Manager ID"
            autoComplete="off"
            value={managerId}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setManagerId(e.currentTarget.value)}
            required
          />
          <PasswordInput
            label="Manager PIN"
            inputMode="numeric"
            autoComplete="off"
            maxLength={6}
            value={pin}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setPin(e.currentTarget.value.replace(/\D/g, ''))}
            required
          />
        </Group>
        <Text size="sm" c="dimmed">The server re-prices the order and records which manager approved this comp.</Text>

        {error && <Alert color="red" variant="light">{error}</Alert>}

        <Group justify="flex-end">
          <Button variant="default" onClick={close} disabled={busy}>Cancel</Button>
          <Button color="orange.9" loading={busy} disabled={!ready} onClick={submit}>Approve comp</Button>
        </Group>
      </Stack>
    </Modal>
  );
}
