import { Alert, Button, Group, NumberInput, Paper, SimpleGrid, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { IconCash, IconCreditCard, IconGift } from '@tabler/icons-react';
import { useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiError, api, describeError, type CreatePaymentRequest, type Order, type Payment } from '../api/client';
import { CardTerminal, type ChargeOutcome } from '../components/CardTerminal';
import { CompDialog } from '../components/CompDialog';
import { OrderSummary } from '../components/OrderSummary';
import { StepLayout } from '../components/StepLayout';
import { formatCents, formatMoney, quickCashAmounts } from '../money';

type Tender = 'CARD' | 'CASH';

interface Props {
  order: Order;
  onPaid: (payment: Payment) => void;
  /** A comp re-priced the order (new total and version from the server). */
  onOrderChange: (order: Order) => void;
  /** A comp brought the total to $0.00; the server closed the order. */
  onCompedInFull: (order: Order) => void;
}

export function PaymentStep({ order, onPaid, onOrderChange, onCompedInFull }: Props) {
  const [compOpen, setCompOpen] = useState(false);
  const [tender, setTender] = useState<Tender>('CARD');
  const [cash, setCash] = useState<string | number>('');
  const [busy, setBusy] = useState(false);
  const [terminalActive, setTerminalActive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // One key per payment attempt. Kept across a network failure so sending the same payment
  // again is a safe retry; replaced once the server has answered or the payment changes.
  const attempt = useRef<{ key: string; body: string } | null>(null);
  const locked = busy || terminalActive;

  const totalCents = order.total.amountCents;
  const quickAmounts = useMemo(() => quickCashAmounts(totalCents), [totalCents]);
  const tenderedCents = typeof cash === 'number' ? Math.round(cash * 100) : NaN;
  const enoughCash = Number.isFinite(tenderedCents) && tenderedCents >= totalCents;

  async function submit(body: CreatePaymentRequest): Promise<ChargeOutcome> {
    const bodyJson = JSON.stringify(body);
    if (attempt.current?.body !== bodyJson) {
      attempt.current = { key: crypto.randomUUID(), body: bodyJson };
    }
    setBusy(true);
    setError(null);
    try {
      return { kind: 'approved', payment: await api.pay(order, body, attempt.current.key) };
    } catch (e) {
      if (!(e instanceof ApiError)) {
        setError(`${describeError(e)} Trying the same card again is safe: the guest won't be charged twice.`);
        return { kind: 'failed' };
      }
      attempt.current = null;
      if (e.status === 402) {
        const code = (e.problem as unknown as { declineCode?: string } | null)?.declineCode;
        return { kind: 'declined', reason: code ? code.replaceAll('_', ' ') : 'card declined' };
      }
      setError(describeError(e));
      return { kind: 'failed' };
    } finally {
      setBusy(false);
    }
  }

  async function payCash() {
    const outcome = await submit({ tenderType: 'CASH', cashTenderedCents: tenderedCents });
    if (outcome.kind === 'approved') onPaid(outcome.payment);
  }

  function comped(updated: Order) {
    setCompOpen(false);
    // The order's total and version changed: start the payment over from the new numbers.
    attempt.current = null;
    setCash('');
    setError(null);
    if (updated.status === 'PAID') {
      onCompedInFull(updated);
    } else {
      onOrderChange(updated);
    }
  }

  return (
    <StepLayout
      main={
      <Stack gap="lg" maw={820}>
        <Group justify="space-between" align="flex-end" wrap="wrap">
          <div>
            <Text fw={600} c="dimmed">
              Total due · Order #{order.orderNumber}{order.guestName ? ` for ${order.guestName}` : ''}
            </Text>
            <Title order={1} className="total-due" fz={{ base: 52, sm: 76 }} lh={1} mt={4}>
              {formatMoney(order.total)}
            </Title>
          </div>
          <Button variant="light" color="orange" size="md" leftSection={<IconGift size={18} />}
                  onClick={() => setCompOpen(true)} disabled={locked}>
            Comp…
          </Button>
        </Group>
        <CompDialog order={order} opened={compOpen} onClose={() => setCompOpen(false)} onComped={comped} />

        <SimpleGrid cols={2} spacing={{ base: 'sm', sm: 'md' }} role="radiogroup" aria-label="Payment method">
          <TenderOption
            selected={tender === 'CARD'}
            disabled={locked}
            onSelect={() => setTender('CARD')}
            icon={<IconCreditCard size={34} stroke={1.6} />}
            label="Card"
            hint="Tap, insert, swipe or key in"
          />
          <TenderOption
            selected={tender === 'CASH'}
            disabled={locked}
            onSelect={() => setTender('CASH')}
            icon={<IconCash size={34} stroke={1.6} />}
            label="Cash"
            hint="Enter amount received"
          />
        </SimpleGrid>

        {tender === 'CARD' ? (
          <CardTerminal
            amount={order.total}
            disabled={busy}
            charge={(cardToken) => submit({ tenderType: 'CARD', cardToken })}
            onApproved={onPaid}
            onActiveChange={setTerminalActive}
          />
        ) : (
          <Paper className="panel" p="lg" radius="xl">
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
                onClick={payCash}
              >
                Complete cash sale
              </Button>
            </Stack>
          </Paper>
        )}

        {error && <Alert color="red" variant="light" title="Payment not completed">{error}</Alert>}
      </Stack>
      }
      aside={
        <OrderSummary
          title="Order summary"
          lines={order.lines.map((l) => ({
            key: l.id,
            menuItemId: l.menuItemId,
            name: l.name,
            quantity: l.quantity,
            lineTotal: l.lineTotal,
            note: l.note,
            comped: l.comped,
          }))}
          subtotal={order.subtotal}
          discount={order.discount}
          compTotal={order.compTotal}
          tax={order.tax}
          total={order.total}
          taxRateBps={order.taxRateBps}
        />
      }
    />
  );
}

function TenderOption({ selected, disabled, onSelect, icon, label, hint }: {
  selected: boolean;
  disabled?: boolean;
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
      disabled={disabled}
      mih={{ base: 84, sm: 112 }}
      px={{ base: 'sm', sm: 'lg' }}
      py={{ base: 'sm', sm: 'md' }}
      style={{
        opacity: disabled && !selected ? 0.5 : 1,
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        borderRadius: 'var(--mantine-radius-lg)',
        border: `2px solid ${selected ? 'var(--mantine-primary-color-filled)' : 'var(--register-line)'}`,
        background: selected ? 'var(--mantine-primary-color-light)' : '#fff',
        color: selected ? 'var(--mantine-primary-color-filled)' : 'var(--mantine-color-dimmed)',
      }}
    >
      {icon}
      <Stack gap={0}>
        <Text fw={700} fz={{ base: 18, sm: 22 }} c="var(--mantine-color-text)">{label}</Text>
        <Text c="dimmed" fz={{ base: 'xs', sm: 'sm' }} lh={1.3}>{hint}</Text>
      </Stack>
    </UnstyledButton>
  );
}
