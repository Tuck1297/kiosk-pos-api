import { Badge, Box, Button, Group, SegmentedControl, SimpleGrid, Stack, Text, UnstyledButton } from '@mantine/core';
import { useMemo, useState } from 'react';
import type { MenuItem } from '../api/client';
import type { CartLine } from '../cart';
import { OrderSummary, type SummaryLine } from '../components/OrderSummary';
import { formatMoney } from '../money';
import type { QuoteState } from '../useQuote';

const CATEGORY_LABELS: Record<string, string> = {
  ENTREE: 'Entrees',
  SNACK: 'Snacks',
  DESSERT: 'Desserts',
  BEVERAGE: 'Beverages',
  MERCHANDISE: 'Merchandise',
};

interface Props {
  menu: MenuItem[];
  cart: CartLine[];
  quote: QuoteState;
  onAdd: (menuItemId: string) => void;
  onChangeQuantity: (menuItemId: string, delta: number) => void;
  onNext: () => void;
}

export function ItemsStep({ menu, cart, quote, onAdd, onChangeQuantity, onNext }: Props) {
  const [category, setCategory] = useState('ALL');

  const categories = useMemo(() => {
    const present = [...new Set(menu.map((m) => m.category))];
    return [{ value: 'ALL', label: 'All items' }, ...present.map((c) => ({ value: c, label: CATEGORY_LABELS[c] ?? c }))];
  }, [menu]);

  const visible = useMemo(
    () => menu
      .filter((m) => category === 'ALL' || m.category === category)
      .sort((a, b) => Number(b.available) - Number(a.available) || a.name.localeCompare(b.name)),
    [menu, category],
  );

  const menuById = useMemo(() => new Map(menu.map((m): [string, MenuItem] => [m.id, m])), [menu]);
  const quantities = useMemo(() => new Map(cart.map((l): [string, number] => [l.menuItemId, l.quantity])), [cart]);

  // Names come from the menu; a line's price shows only once the server has priced that exact line.
  const priced = quote.loading ? undefined : quote.quote;
  const lines: SummaryLine[] = cart.map((l) => {
    const q = priced?.lines.find((p) => p.menuItemId === l.menuItemId && p.quantity === l.quantity);
    return {
      menuItemId: l.menuItemId,
      name: menuById.get(l.menuItemId)?.name ?? 'Item',
      quantity: l.quantity,
      lineTotal: q?.lineTotal,
    };
  });
  const ready = cart.length > 0 && !quote.loading && !quote.error && quote.quote !== null;

  return (
    <Group align="flex-start" gap="xl" wrap="wrap">
      <Stack gap="lg" style={{ flex: '999 1 520px', minWidth: 0 }}>
        <SegmentedControl data={categories} value={category} onChange={(v: string) => setCategory(v)} size="md" />
        <SimpleGrid cols={{ base: 2, sm: 3, xl: 4 }} spacing="md">
          {visible.map((item) => {
            const qty = quantities.get(item.id);
            return (
              <UnstyledButton
                key={item.id}
                onClick={() => onAdd(item.id)}
                disabled={!item.available}
                aria-label={item.available ? `Add ${item.name}, ${formatMoney(item.price)}` : `${item.name}, sold out`}
                style={{
                  minHeight: 132,
                  padding: 16,
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: 8,
                  border: `1px ${item.available ? 'solid' : 'dashed'} var(--mantine-color-default-border)`,
                  borderRadius: 'var(--mantine-radius-md)',
                  background: item.available ? 'var(--mantine-color-body)' : 'var(--mantine-color-gray-1)',
                  cursor: item.available ? 'pointer' : 'not-allowed',
                }}
              >
                <Group justify="space-between" wrap="nowrap">
                  <Text size="xs" fw={700} tt="uppercase" c="dimmed">{CATEGORY_LABELS[item.category] ?? item.category}</Text>
                  {qty && <Badge circle size="lg">{qty}</Badge>}
                </Group>
                <Text fw={700} size="lg" td={item.available ? undefined : 'line-through'} c={item.available ? undefined : 'dimmed'}>
                  {item.name}
                </Text>
                {item.available ? (
                  <Text fw={800} size="xl">{formatMoney(item.price)}</Text>
                ) : (
                  <Box><Badge color="red" variant="light">Sold out</Badge></Box>
                )}
              </UnstyledButton>
            );
          })}
        </SimpleGrid>
      </Stack>

      <Box style={{ flex: '1 1 360px', maxWidth: 480 }}>
        <OrderSummary
          title="Current order"
          lines={lines}
          subtotal={priced?.subtotal}
          tax={priced?.tax}
          total={priced?.total}
          taxRateBps={priced?.taxRate.rateBps}
          loading={quote.loading}
          error={quote.error}
          onChangeQuantity={onChangeQuantity}
          footer={
            <Button size="xl" fullWidth disabled={!ready} onClick={onNext}>
              Review order
            </Button>
          }
        />
      </Box>
    </Group>
  );
}
