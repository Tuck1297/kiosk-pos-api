import { Alert, Box, Button, Group, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconArrowLeft } from '@tabler/icons-react';
import { useState, type ChangeEvent } from 'react';
import type { OrderQuote } from '../api/client';
import { OrderSummary } from '../components/OrderSummary';
import { formatMoney } from '../money';

interface Props {
  quote: OrderQuote;
  busy: boolean;
  error: string | null;
  onBack: () => void;
  onConfirm: (guestName: string) => void;
}

export function ReviewStep({ quote, busy, error, onBack, onConfirm }: Props) {
  const [guestName, setGuestName] = useState('');

  return (
    <Group align="flex-start" gap="xl" wrap="wrap">
      <Stack gap="lg" style={{ flex: '999 1 520px', minWidth: 0, maxWidth: 720 }}>
        <div>
          <Text fw={600} c="dimmed">Total due</Text>
          <Title order={1} fz={64} lh={1.1}>{formatMoney(quote.total)}</Title>
          <Text c="dimmed" mt={4}>Read the order back to the guest before taking payment.</Text>
        </div>

        <TextInput
          label="Guest name"
          description="Called out when the order is ready. Optional."
          size="lg"
          maxLength={40}
          value={guestName}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setGuestName(e.currentTarget.value)}
        />

        {error && <Alert color="red" variant="light" title="Couldn't create the order">{error}</Alert>}

        <Group gap="md">
          <Button variant="default" size="lg" leftSection={<IconArrowLeft size={18} />} onClick={onBack} disabled={busy}>
            Edit items
          </Button>
          <Button size="lg" loading={busy} onClick={() => onConfirm(guestName.trim())}>
            Continue to payment
          </Button>
        </Group>
      </Stack>

      <Box style={{ flex: '1 1 360px', maxWidth: 480 }}>
        <OrderSummary
          title="Order summary"
          lines={quote.lines.map((l) => ({
            menuItemId: l.menuItemId,
            name: l.name,
            quantity: l.quantity,
            lineTotal: l.lineTotal,
            note: l.note,
          }))}
          subtotal={quote.subtotal}
          tax={quote.tax}
          total={quote.total}
          taxRateBps={quote.taxRate.rateBps}
          taxName={quote.taxRate.name}
        />
      </Box>
    </Group>
  );
}
