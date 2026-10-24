import { Badge, Box, Button, Flex, Group, Loader, Paper, SegmentedControl, SimpleGrid, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconCircleCheck, IconCircleX, IconKeyboard, IconNfc } from '@tabler/icons-react';
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import type { Money, Payment } from '../api/client';
import {
  BRAND_LABELS, WALLET, describeCard, detectBrand, formatCardNumber, formatExpiry, tokenizeCard, validateKeyedCard,
  type KeyedCard, type KeyedCardErrors, type PresentedCard,
} from '../card';
import { formatMoney } from '../money';

export type ChargeOutcome =
  | { kind: 'approved'; payment: Payment }
  | { kind: 'declined'; reason: string }
  /** The request failed; the parent shows why. */
  | { kind: 'failed' };

type EntryMethod = 'TAP' | 'INSERT' | 'SWIPE';
type Phase =
  | { name: 'waiting' }
  | { name: 'reading'; method: EntryMethod | 'KEYED' }
  | { name: 'authorizing'; card: PresentedCard }
  | { name: 'approved'; card: PresentedCard }
  | { name: 'declined'; card: PresentedCard; reason: string };

const READ_TIME_MS: Record<EntryMethod, number> = { TAP: 600, INSERT: 1500, SWIPE: 800 };
/** How long "Approved" stays on screen before the register moves on. */
const APPROVED_HOLD_MS = 1200;

interface Props {
  amount: Money;
  disabled?: boolean;
  /** Sends the token to the API. */
  charge: (token: string) => Promise<ChargeOutcome>;
  onApproved: (payment: Payment) => void;
  /** True while a card is being read or authorized, so the rest of the screen can lock. */
  onActiveChange?: (active: boolean) => void;
}

/**
 * Stands in for the card reader on the counter. The guest's card is "presented" from the demo
 * wallet (or keyed in), the reader turns it into a token, and only that token reaches the API.
 */
export function CardTerminal({ amount, disabled, charge, onApproved, onActiveChange }: Props) {
  const [phase, setPhase] = useState<Phase>({ name: 'waiting' });
  const [method, setMethod] = useState<EntryMethod>('TAP');
  const [keying, setKeying] = useState(false);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const active = phase.name === 'reading' || phase.name === 'authorizing' || phase.name === 'approved';
  useEffect(() => onActiveChange?.(active), [active, onActiveChange]);

  async function present(card: PresentedCard, how: EntryMethod | 'KEYED') {
    setPhase({ name: 'reading', method: how });
    await wait(how === 'KEYED' ? 300 : READ_TIME_MS[how]);
    if (!alive.current) return;

    setPhase({ name: 'authorizing', card });
    const outcome = await charge(card.token);
    if (!alive.current) return;

    if (outcome.kind === 'approved') {
      setPhase({ name: 'approved', card });
      await wait(APPROVED_HOLD_MS);
      if (alive.current) onApproved(outcome.payment);
    } else if (outcome.kind === 'declined') {
      setPhase({ name: 'declined', card, reason: outcome.reason });
    } else {
      setPhase({ name: 'waiting' });
    }
  }

  const canPresent = !disabled && (phase.name === 'waiting' || phase.name === 'declined');

  return (
    <Paper className="panel" p={{ base: 'sm', sm: 'lg' }} radius="xl">
      <Stack gap="lg">
        <ReaderScreen amount={amount} phase={phase} method={method} />

        {keying ? (
          <KeyInForm
            disabled={!canPresent}
            onCancel={() => setKeying(false)}
            onSubmit={(card) => present(card, 'KEYED')}
          />
        ) : (
          <Stack gap="sm">
            <Group justify="space-between" wrap="wrap" gap="sm">
              <div>
                <Text fw={700}>Guest's card</Text>
                <Text size="sm" c="dimmed">Demo wallet: pick how the guest pays, then tap a card.</Text>
              </div>
              <SegmentedControl
                value={method}
                onChange={(v: string) => setMethod(v as EntryMethod)}
                data={[
                  { value: 'TAP', label: 'Tap' },
                  { value: 'INSERT', label: 'Insert' },
                  { value: 'SWIPE', label: 'Swipe' },
                ]}
                disabled={!canPresent}
              />
            </Group>
            <SimpleGrid cols={{ base: 2, xs: 3, xl: 5 }} spacing="sm">
              {WALLET.map((card) => (
                <UnstyledButton
                  key={card.token}
                  onClick={() => present(card, method)}
                  disabled={!canPresent}
                  aria-label={`${describeCard(card.brand, card.last4)}, ${card.outcome.toLowerCase()} in this demo`}
                  style={{
                    aspectRatio: '1.586',
                    borderRadius: 10,
                    padding: 12,
                    color: 'white',
                    background: `linear-gradient(135deg, ${card.color}, ${card.color}cc)`,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    opacity: canPresent ? 1 : 0.5,
                    cursor: canPresent ? 'pointer' : 'not-allowed',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.25)',
                  }}
                >
                  <Text fw={800} size="sm" c="white">{card.label}</Text>
                  <div>
                    <Text size="sm" c="white" style={{ fontVariantNumeric: 'tabular-nums', letterSpacing: 1 }}>
                      •••• {card.last4}
                    </Text>
                    <Text size="xs" c="white" opacity={0.85}>{card.outcome}</Text>
                  </div>
                </UnstyledButton>
              ))}
            </SimpleGrid>
            <Group>
              <Button variant="subtle" leftSection={<IconKeyboard size={18} />} onClick={() => setKeying(true)}
                      disabled={!canPresent}>
                Key in card number
              </Button>
            </Group>
          </Stack>
        )}
      </Stack>
    </Paper>
  );
}

function ReaderScreen({ amount, phase, method }: { amount: Money; phase: Phase; method: EntryMethod }) {
  const tone =
    phase.name === 'approved' ? 'var(--mantine-color-teal-8)'
      : phase.name === 'declined' ? 'var(--mantine-color-red-8)'
        : 'var(--register-ink)';

  let icon = <IconNfc size={44} stroke={1.5} aria-hidden />;
  let headline = method === 'INSERT' ? 'Insert card' : method === 'SWIPE' ? 'Swipe card' : 'Tap, insert or swipe';
  let detail = 'Waiting for the guest';
  if (phase.name === 'reading') {
    icon = <Loader color="white" size={36} />;
    headline = phase.method === 'KEYED' ? 'Card keyed in' : phase.method === 'INSERT' ? 'Reading chip' : 'Reading card';
    detail = phase.method === 'INSERT' ? "Don't remove the card" : 'Hold still…';
  } else if (phase.name === 'authorizing') {
    icon = <Loader color="white" size={36} />;
    headline = 'Authorizing';
    detail = describeCard(phase.card.brand, phase.card.last4);
  } else if (phase.name === 'approved') {
    icon = <IconCircleCheck size={44} stroke={1.5} aria-hidden />;
    headline = 'Approved';
    detail = `${describeCard(phase.card.brand, phase.card.last4)} · remove card`;
  } else if (phase.name === 'declined') {
    icon = <IconCircleX size={44} stroke={1.5} aria-hidden />;
    headline = 'Declined';
    detail = `${capitalize(phase.reason)} · the guest was not charged. Try another card or cash.`;
  }

  return (
    <Box
      role="status"
      aria-live="polite"
      className="reader-screen"
      p={{ base: 'md', sm: 'lg' }}
      mih={{ base: 132, sm: 156 }}
      style={{ background: tone, color: 'white', borderRadius: 'var(--mantine-radius-lg)', transition: 'background 200ms ease' }}
    >
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={2}>
          <Text size="sm" c="white" opacity={0.75} fw={500}>Card reader</Text>
          <Text className="display-number" fz={{ base: 36, sm: 48 }} lh={1.05} c="white" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatMoney(amount)}
          </Text>
        </Stack>
        {icon}
      </Group>
      <Text fw={700} fz={{ base: 19, sm: 22 }} c="white" mt="sm">{headline}</Text>
      <Text c="white" opacity={0.85}>{detail}</Text>
    </Box>
  );
}

function KeyInForm({ disabled, onSubmit, onCancel }: {
  disabled: boolean;
  onSubmit: (card: PresentedCard) => void;
  onCancel: () => void;
}) {
  const [card, setCard] = useState<KeyedCard>({ number: '', expiry: '', cvc: '' });
  const [errors, setErrors] = useState<KeyedCardErrors>({});
  const brand = detectBrand(card.number);

  function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateKeyedCard(card);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const token = tokenizeCard(card);
    // Drop the number as soon as it's tokenized.
    setCard({ number: '', expiry: '', cvc: '' });
    onSubmit(token);
  }

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="sm">
        <Group justify="space-between" gap={4}>
          <Text fw={700}>Key in card</Text>
          <Text size="xs" c="dimmed">The number is tokenized here and never sent to the API.</Text>
        </Group>
        <TextInput
          label="Card number"
          size="md"
          inputMode="numeric"
          autoComplete="off"
          placeholder="4242 4242 4242 4242"
          value={card.number}
          error={errors.number}
          rightSection={brand ? <Badge variant="light" size="sm" tt="none">{BRAND_LABELS[brand]}</Badge> : null}
          rightSectionWidth={brand ? 130 : undefined}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            const number = formatCardNumber(e.currentTarget.value);
            setCard((c) => ({ ...c, number }));
          }}
        />
        <SimpleGrid cols={2} spacing="sm">
          <TextInput
            label="Expiry"
            size="md"
            inputMode="numeric"
            autoComplete="off"
            placeholder="MM/YY"
            value={card.expiry}
            error={errors.expiry}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              const expiry = formatExpiry(e.currentTarget.value);
              setCard((c) => ({ ...c, expiry }));
            }}
          />
          <TextInput
            label="CVC"
            size="md"
            inputMode="numeric"
            autoComplete="off"
            placeholder={brand === 'AMEX' ? '4 digits' : '3 digits'}
            value={card.cvc}
            error={errors.cvc}
            onChange={(e: ChangeEvent<HTMLInputElement>) => {
              const cvc = e.currentTarget.value.replace(/\D/g, '').slice(0, 4);
              setCard((c) => ({ ...c, cvc }));
            }}
          />
        </SimpleGrid>
        <Text size="xs" c="dimmed">
          Test numbers: 4242 4242 4242 4242 approves; 4000 0000 0000 0002 declines; 4000 0000 0000 9995 has
          insufficient funds. Any future expiry.
        </Text>
        <Flex direction={{ base: 'column', xs: 'row' }} gap="sm">
          <Button type="submit" size="lg" disabled={disabled}>Charge card</Button>
          <Button variant="default" size="lg" onClick={onCancel} disabled={disabled}>Use the reader</Button>
        </Flex>
      </Stack>
    </form>
  );
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function capitalize(s: string): string {
  return s ? s[0].toUpperCase() + s.slice(1) : s;
}
