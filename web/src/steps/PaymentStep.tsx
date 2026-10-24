import { Alert, Box, Button, Group, NumberInput, Paper, Select, SimpleGrid, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { IconAlertTriangle, IconCash, IconCreditCard } from '@tabler/icons-react';
import { useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiError, api, describeError, type CreatePaymentRequest, type Order, type Payment } from '../api/client';
import { OrderSummary } from '../components/OrderSummary';
import { formatCents, formatMoney, quickCashAmounts } from '../money';

type Tender = 'CARD' | 'CASH';

/** Stand-ins for a physical card reader; the API's mock processor approves or declines by token. */
const DEMO_CARDS = [
  { value: 'tok_visa_4242', label: 'Visa ending 4242 (approves)' },
  { value: 'tok_decline_insufficient_funds_0002', label: 'Card ending 0002 (declines)' },
];

interface Props {
  order: Order;
  onPaid: (payment: Payment) => void;
}

export function PaymentStep({ order, onPaid }: Props) {
  const [tender, setTender] = useState<Tender>('CARD');
  const [card, setCard] = useState<string>(DEMO_CARDS[0].value);
  const [cash, setCash] = useState<string | number>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [decline, setDecline] = useState<string | null>(null);
  // One key per payment attempt. Kept across a network failure so tapping again is a safe
  // retry; replaced once the server has answered.
  const attemptKey = useRef<string | null>(null);

  const totalCents = order.total.amountCents;
  const quickAmounts = useMemo(() => quickCashAmounts(totalCents), [totalCents]);
  const tenderedCents = typeof cash === 'number' ? Math.round(cash * 100) : NaN;
  const enoughCash = Number.isFinite(tenderedCents) && tenderedCents >= totalCents;

  async function pay(body: CreatePaymentRequest) {
    attemptKey.current ??= crypto.randomUUID();
    setBusy(true);
    setError(null);
    try {
      const payment = await api.pay(order, body, attemptKey.current);
      onPaid(payment);
    } catch (e) {
      if (e instanceof ApiError) {
        attemptKey.current = null;
        if (e.status === 402) {
          const code = (e.problem as unknown as { declineCode?: string } | null)?.declineCode;
          setDecline(code ? code.replaceAll('_', ' ') : 'card declined');
        } else {
          setError(describeError(e));
        }
      } else {
        setError(`${describeError(e)} Tapping again is safe: the guest won't be charged twice.`);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Group align="flex-start" gap="xl" wrap="wrap">
      <Stack gap="lg" style={{ flex: '999 1 520px', minWidth: 0, maxWidth: 820 }}>
        <div>
          <Text fw={600} c="dimmed">
            Total due · Order #{order.orderNumber}{order.guestName ? ` for ${order.guestName}` : ''}
          </Text>
          <Title order={1} fz={72} lh={1.1} style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatMoney(order.total)}
          </Title>
        </div>

        {decline && (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle />} title={`Card declined: ${decline}`}>
            The guest was not charged. Try another card, or switch to cash.
          </Alert>
        )}

        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md" role="radiogroup" aria-label="Payment method">
          <TenderOption
            selected={tender === 'CARD'}
            onSelect={() => setTender('CARD')}
            icon={<IconCreditCard size={40} stroke={1.6} />}
            label="Card"
            hint="Tap, insert or swipe"
          />
          <TenderOption
            selected={tender === 'CASH'}
            onSelect={() => setTender('CASH')}
            icon={<IconCash size={40} stroke={1.6} />}
            label="Cash"
            hint="Enter amount received"
          />
        </SimpleGrid>

        {tender === 'CARD' ? (
          <Paper withBorder p="lg" radius="md" bg="gray.0">
            <Stack gap="md">
              <div>
                <Text fw={700} size="xl">Waiting for card</Text>
                <Text c="dimmed">The order stays open until a payment is approved.</Text>
              </div>
              <Select
                label="Demo card reader"
                description="Stands in for the physical reader in this demo."
                data={DEMO_CARDS}
                value={card}
                onChange={(v: string | null) => v && setCard(v)}
                allowDeselect={false}
                size="md"
              />
              <Button size="xl" loading={busy} onClick={() => pay({ tenderType: 'CARD', cardToken: card })}>
                Charge {formatMoney(order.total)}
              </Button>
            </Stack>
          </Paper>
        ) : (
          <Paper withBorder p="lg" radius="md">
            <Stack gap="md">
              <NumberInput
                label="Cash received"
                size="xl"
                prefix="$"
                decimalScale={2}
                fixedDecimalScale
                thousandSeparator=","
                min={0}
                value={cash}
                onChange={setCash}
                placeholder={formatCents(totalCents, order.total.currency)}
              />
              <SimpleGrid cols={{ base: 3, sm: 5 }} spacing="sm" role="group" aria-label="Quick cash amounts">
                {quickAmounts.map((cents, i) => {
                  const selected = tenderedCents === cents;
                  return (
                    <Button
                      key={cents}
                      size="lg"
                      variant={selected ? 'filled' : 'default'}
                      aria-pressed={selected}
                      onClick={() => setCash(cents / 100)}
                    >
                      {i === 0 && cents === totalCents ? 'Exact' : formatCents(cents, order.total.currency)}
                    </Button>
                  );
                })}
              </SimpleGrid>
              <Text size="sm" c="dimmed">The server checks the amount and computes the change.</Text>
              <Button
                size="xl"
                loading={busy}
                disabled={!enoughCash}
                onClick={() => pay({ tenderType: 'CASH', cashTenderedCents: tenderedCents })}
              >
                Complete cash sale
              </Button>
            </Stack>
          </Paper>
        )}

        {error && <Alert color="red" variant="light" title="Payment not completed">{error}</Alert>}
      </Stack>

      <Box style={{ flex: '1 1 360px', maxWidth: 480 }}>
        <OrderSummary
          title="Order summary"
          lines={order.lines.map((l) => ({
            menuItemId: l.menuItemId,
            name: l.name,
            quantity: l.quantity,
            lineTotal: l.lineTotal,
            note: l.note,
          }))}
          subtotal={order.subtotal}
          tax={order.tax}
          total={order.total}
          taxRateBps={order.taxRateBps}
        />
      </Box>
    </Group>
  );
}

function TenderOption({ selected, onSelect, icon, label, hint }: {
  selected: boolean;
  onSelect: () => void;
  icon: ReactNode;
  label: string;
  hint: string;
}) {
  return (
    <UnstyledButton
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      style={{
        minHeight: 112,
        padding: '20px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: 18,
        borderRadius: 'var(--mantine-radius-lg)',
        border: `2px solid ${selected ? 'var(--mantine-primary-color-filled)' : 'var(--mantine-color-default-border)'}`,
        background: selected ? 'var(--mantine-primary-color-light)' : 'var(--mantine-color-body)',
        color: selected ? 'var(--mantine-primary-color-filled)' : 'var(--mantine-color-dimmed)',
      }}
    >
      {icon}
      <Stack gap={0}>
        <Text fw={700} fz={22} c="var(--mantine-color-text)">{label}</Text>
        <Text c="dimmed">{hint}</Text>
      </Stack>
    </UnstyledButton>
  );
}
