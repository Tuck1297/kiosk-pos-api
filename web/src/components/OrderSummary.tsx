import { ActionIcon, Alert, Divider, Group, Loader, Paper, Stack, Text, Title } from '@mantine/core';
import { IconMinus, IconPlus } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import type { Money } from '../api/client';
import { formatMoney, formatRate } from '../money';

export interface SummaryLine {
  menuItemId: string;
  name: string;
  quantity: number;
  /** Absent until the server has priced this exact line. */
  lineTotal?: Money;
  note?: string | null;
}

interface Props {
  title: string;
  lines: SummaryLine[];
  subtotal?: Money;
  tax?: Money;
  total?: Money;
  taxRateBps?: number;
  taxName?: string;
  loading?: boolean;
  error?: string | null;
  onChangeQuantity?: (menuItemId: string, delta: number) => void;
  footer?: ReactNode;
}

/** The order on the right of every step. Every amount shown here came from the server. */
export function OrderSummary({
  title, lines, subtotal, tax, total, taxRateBps, taxName, loading, error, onChangeQuantity, footer,
}: Props) {
  return (
    <Paper withBorder p="lg" radius="md">
      <Stack gap="md">
        <Group justify="space-between">
          <Title order={3}>{title}</Title>
          {loading && (
            <Group gap={6}>
              <Loader size="xs" />
              <Text size="sm" c="dimmed">Updating prices</Text>
            </Group>
          )}
        </Group>

        {lines.length === 0 ? (
          <Text c="dimmed">Tap items to start an order.</Text>
        ) : (
          <Stack gap="sm">
            {lines.map((line) => (
              <Group key={line.menuItemId} justify="space-between" wrap="nowrap" align="center">
                <Stack gap={0} style={{ minWidth: 0, flex: 1 }}>
                  <Text fw={600}>{line.name}</Text>
                  {line.note && <Text size="sm" fs="italic" c="orange.9">{line.note}</Text>}
                </Stack>
                {onChangeQuantity ? (
                  <Group gap={4} wrap="nowrap">
                    <ActionIcon variant="default" size="xl" aria-label={`Remove one ${line.name}`}
                                onClick={() => onChangeQuantity(line.menuItemId, -1)}>
                      <IconMinus size={18} />
                    </ActionIcon>
                    <Text fw={700} w={28} ta="center">{line.quantity}</Text>
                    <ActionIcon variant="default" size="xl" aria-label={`Add one ${line.name}`}
                                onClick={() => onChangeQuantity(line.menuItemId, 1)}>
                      <IconPlus size={18} />
                    </ActionIcon>
                  </Group>
                ) : (
                  <Text fw={700}>{line.quantity}×</Text>
                )}
                <Text w={80} ta="right" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {line.lineTotal ? formatMoney(line.lineTotal) : '—'}
                </Text>
              </Group>
            ))}
          </Stack>
        )}

        {error && <Alert color="red" variant="light">{error}</Alert>}

        {lines.length > 0 && (
          <>
            <Divider />
            <Stack gap={4} style={{ fontVariantNumeric: 'tabular-nums' }}>
              <Group justify="space-between">
                <Text c="dimmed">Subtotal</Text>
                <Text>{subtotal ? formatMoney(subtotal) : '—'}</Text>
              </Group>
              <Group justify="space-between">
                <Text c="dimmed">
                  Tax{taxRateBps !== undefined ? ` (${formatRate(taxRateBps)})` : ''}
                  {taxName ? ` · ${taxName}` : ''}
                </Text>
                <Text>{tax ? formatMoney(tax) : '—'}</Text>
              </Group>
              <Group justify="space-between" mt={6}>
                <Text fw={700} size="lg">Total</Text>
                <Text fw={800} size="xl">{total ? formatMoney(total) : '—'}</Text>
              </Group>
            </Stack>
          </>
        )}

        {footer}
      </Stack>
    </Paper>
  );
}
