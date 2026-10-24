import { Badge, Box, Button, Group, SegmentedControl, SimpleGrid, Stack, Text, UnstyledButton } from '@mantine/core';
import { useMemo, useRef, useState } from 'react';
import type { MenuItem } from '../api/client';
import type { CartLine } from '../cart';
import { OrderSummary, type SummaryLine } from '../components/OrderSummary';
import { StepLayout } from '../components/StepLayout';
import { formatMoney } from '../money';
import type { QuoteState } from '../useQuote';

const CATEGORY_LABELS: Record<string, string> = {
  ENTREE: 'Entrées',
  SNACK: 'Snacks',
  DESSERT: 'Desserts',
  BEVERAGE: 'Drinks',
  MERCHANDISE: 'Merchandise',
};

/** Each category's color (theme.css), shared by its tab and its tiles. */
const categoryColor = (category: string) => `var(--cat-${category}, var(--mantine-color-gray-6))`;

function CategoryTag({ category }: { category: string }) {
  return (
    <Group gap={6} wrap="nowrap">
      <span className="category-dot" style={{ background: categoryColor(category) }} aria-hidden />
      <Text size="sm" fw={600} c={categoryColor(category)}>{CATEGORY_LABELS[category] ?? category}</Text>
    </Group>
  );
}

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
    return [
      { value: 'ALL', label: 'All items' },
      ...present.map((c) => ({
        value: c,
        label: (
          <Group gap={6} wrap="nowrap" justify="center">
            <span className="category-dot" style={{ background: categoryColor(c) }} aria-hidden />
            <span>{CATEGORY_LABELS[c] ?? c}</span>
          </Group>
        ),
      })),
    ];
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
  const itemCount = cart.reduce((n, l) => n + l.quantity, 0);
  const summaryRef = useRef<HTMLDivElement | null>(null);

  return (
    <>
    <StepLayout
      main={
      <Stack gap="md">
        <Box className="scroll-x">
          <SegmentedControl data={categories} value={category} onChange={(v: string) => setCategory(v)} size="md"
                            radius="xl" bg="white" style={{ border: '1px solid var(--register-line)' }} />
        </Box>
        <SimpleGrid cols={{ base: 2, xs: 3, sm: 2, md: 3, xl: 4 }} spacing={{ base: 'sm', sm: 'md' }}>
          {visible.map((item) => {
            const qty = quantities.get(item.id);
            return (
              <UnstyledButton
                key={item.id}
                onClick={() => onAdd(item.id)}
                disabled={!item.available}
                aria-label={item.available ? `Add ${item.name}, ${formatMoney(item.price)}` : `${item.name}, sold out`}
                className="menu-tile"
                data-in-cart={qty ? true : undefined}
                mih={{ base: 108, sm: 132 }}
                p={{ base: 'sm', sm: 'md' }}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: 8,
                  cursor: item.available ? 'pointer' : 'not-allowed',
                }}
              >
                <Group justify="space-between" wrap="nowrap" gap="xs">
                  <CategoryTag category={item.category} />
                  {qty && <span className="cart-count" aria-label={`${qty} in the order`}>{qty}</span>}
                </Group>
                <Text fw={600} fz={{ base: 'md', sm: 'lg' }} lh={1.25} td={item.available ? undefined : 'line-through'} c={item.available ? undefined : 'dimmed'}>
                  {item.name}
                </Text>
                {item.available ? (
                  <Text className="display-number" fz={{ base: 22, sm: 26 }} lh={1}>{formatMoney(item.price)}</Text>
                ) : (
                  <Box><Badge color="red" variant="light" tt="none" size="lg">Sold out</Badge></Box>
                )}
              </UnstyledButton>
            );
          })}
        </SimpleGrid>
      </Stack>
      }
      aside={
      <Box ref={summaryRef} style={{ scrollMarginTop: 8 }}>
        <OrderSummary
          title="Current order"
          lines={lines}
          subtotal={priced?.subtotal}
          discount={priced?.discount}
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
      }
    />

    {/* Phones: the total and the next step stay on screen while the cashier taps items. */}
    {cart.length > 0 && (
      <>
        <Box hiddenFrom="sm" className="checkout-bar-spacer" />
        <Group hiddenFrom="sm" className="checkout-bar" justify="space-between" wrap="nowrap" gap="sm">
          <UnstyledButton
            onClick={() => summaryRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
            aria-label="Show the order"
            style={{ minWidth: 0 }}
          >
            <Text size="sm" c="dimmed" lh={1.2}>{itemCount} {itemCount === 1 ? 'item' : 'items'} · view order</Text>
            <Text className="display-number" fz={26} lh={1.15}>
              {priced ? formatMoney(priced.total) : '…'}
            </Text>
          </UnstyledButton>
          <Button size="lg" disabled={!ready} onClick={onNext} style={{ flexShrink: 0 }}>
            Review order
          </Button>
        </Group>
      </>
    )}
    </>
  );
}
