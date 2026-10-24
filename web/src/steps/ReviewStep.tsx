import { Alert, Box, Button, Group, Loader, Paper, SegmentedControl, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconArrowLeft, IconId, IconTicket } from '@tabler/icons-react';
import { useState, type ChangeEvent, type FormEvent } from 'react';
import type { DiscountRequest, OrderQuote } from '../api/client';
import { OrderSummary } from '../components/OrderSummary';
import { formatMoney } from '../money';

interface Props {
  quote: OrderQuote;
  /** The server is re-pricing (e.g. a discount was just applied or removed). */
  repricing: boolean;
  discount: DiscountRequest | null;
  /** Why a discount was taken off automatically, if it was. */
  discountNotice: string | null;
  /** Checks the discount with the server; resolves to an error message, or null once applied. */
  onApplyDiscount: (discount: DiscountRequest) => Promise<string | null>;
  onRemoveDiscount: () => void;
  busy: boolean;
  error: string | null;
  onBack: () => void;
  onConfirm: (guestName: string) => void;
}

export function ReviewStep({
  quote, repricing, discount, discountNotice, onApplyDiscount, onRemoveDiscount, busy, error, onBack, onConfirm,
}: Props) {
  const [guestName, setGuestName] = useState('');

  return (
    <Group align="flex-start" gap="xl" wrap="wrap">
      <Stack gap="lg" style={{ flex: '999 1 520px', minWidth: 0, maxWidth: 720 }}>
        <div>
          <Group gap="sm">
            <Text fw={600} c="dimmed">Total due</Text>
            {repricing && <Loader size="xs" />}
          </Group>
          <Title order={1} fz={64} lh={1.1} style={{ fontVariantNumeric: 'tabular-nums', opacity: repricing ? 0.5 : 1 }}>
            {formatMoney(quote.total)}
          </Title>
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

        <DiscountPanel
          quote={quote}
          discount={discount}
          notice={discountNotice}
          disabled={busy}
          onApply={onApplyDiscount}
          onRemove={onRemoveDiscount}
        />

        {error && <Alert color="red" variant="light" title="Couldn't create the order">{error}</Alert>}

        <Group gap="md">
          <Button variant="default" size="lg" leftSection={<IconArrowLeft size={18} />} onClick={onBack} disabled={busy}>
            Edit items
          </Button>
          <Button size="lg" loading={busy} disabled={repricing} onClick={() => onConfirm(guestName.trim())}>
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
          discount={quote.discount}
          tax={quote.tax}
          total={quote.total}
          taxRateBps={quote.taxRate.rateBps}
          taxName={quote.taxRate.name}
          loading={repricing}
        />
      </Box>
    </Group>
  );
}

type DiscountKind = 'CODE' | 'EMPLOYEE';

const CODE_FORMAT = /^[A-Z0-9]{3,20}$/;
const EMPLOYEE_ID_FORMAT = /^[A-Z0-9-]{2,16}$/;

function DiscountPanel({ quote, discount, notice, disabled, onApply, onRemove }: {
  quote: OrderQuote;
  discount: DiscountRequest | null;
  notice: string | null;
  disabled: boolean;
  onApply: (discount: DiscountRequest) => Promise<string | null>;
  onRemove: () => void;
}) {
  const [kind, setKind] = useState<DiscountKind>('CODE');
  const [value, setValue] = useState('');
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function apply(e: FormEvent) {
    e.preventDefault();
    const entered = value.trim().toUpperCase();
    // Format only, so a typo gets a quick answer. Whether it applies is the server's call.
    if (kind === 'CODE' && !CODE_FORMAT.test(entered)) {
      setError('Codes are 3 to 20 letters and numbers');
      return;
    }
    if (kind === 'EMPLOYEE' && !EMPLOYEE_ID_FORMAT.test(entered)) {
      setError('Enter an employee ID, like C2001');
      return;
    }
    setChecking(true);
    setError(null);
    const problem = await onApply(kind === 'CODE' ? { code: entered } : { employeeId: entered });
    setChecking(false);
    if (problem) {
      setError(problem);
    } else {
      setValue('');
    }
  }

  const applied = quote.discount;
  if (discount) {
    return (
      <Paper withBorder p="md" radius="md" bg="teal.0">
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Group gap="sm" wrap="nowrap">
            {discount.employeeId ? <IconId size={24} aria-hidden /> : <IconTicket size={24} aria-hidden />}
            <div>
              <Text fw={700} c="teal.9">{applied ? applied.label : 'Applying discount…'}</Text>
              {applied && <Text size="sm" c="teal.9">−{formatMoney(applied.amount)} before tax</Text>}
            </div>
          </Group>
          <Button variant="default" onClick={onRemove} disabled={disabled}>Remove</Button>
        </Group>
      </Paper>
    );
  }

  return (
    <Paper withBorder p="md" radius="md">
      <form onSubmit={apply} noValidate>
        <Stack gap="sm">
          <Group justify="space-between" wrap="wrap" gap="sm">
            <Text fw={700}>Discount</Text>
            <SegmentedControl
              value={kind}
              onChange={(v: string) => {
                setKind(v as DiscountKind);
                setError(null);
              }}
              data={[
                { value: 'CODE', label: 'Promo code' },
                { value: 'EMPLOYEE', label: 'Employee ID' },
              ]}
            />
          </Group>
          {notice && <Alert color="yellow" variant="light">{notice}</Alert>}
          <Group align="flex-start" gap="sm" wrap="nowrap">
            <TextInput
              style={{ flex: 1 }}
              size="md"
              aria-label={kind === 'CODE' ? 'Promo code' : 'Employee ID'}
              placeholder={kind === 'CODE' ? 'e.g. PARKFAN10' : 'e.g. C2001'}
              description={kind === 'CODE'
                ? 'One discount per order.'
                : "For a staff member's own purchase. The server checks the ID is active."}
              autoComplete="off"
              autoCapitalize="characters"
              maxLength={20}
              value={value}
              error={error}
              onChange={(e: ChangeEvent<HTMLInputElement>) => {
                setValue(e.currentTarget.value.toUpperCase());
                setError(null);
              }}
            />
            <Button type="submit" size="md" loading={checking} disabled={disabled || value.trim() === ''}>
              Apply
            </Button>
          </Group>
        </Stack>
      </form>
    </Paper>
  );
}
