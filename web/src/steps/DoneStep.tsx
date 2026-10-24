import { Button, Center, Paper, Stack, Text, Title } from '@mantine/core';
import { IconCircleCheck } from '@tabler/icons-react';
import type { Order, Payment } from '../api/client';
import { formatMoney } from '../money';

interface Props {
  order: Order;
  /** Absent when the order was comped in full: nothing was collected. */
  payment: Payment | null;
  onNextSale: () => void;
}

export function DoneStep({ order, payment, onNextSale }: Props) {
  const lastComp = order.comps[order.comps.length - 1];
  return (
    <Center py="xl">
      <Stack align="center" gap="lg" maw={560} w="100%">
        <IconCircleCheck size={72} stroke={1.5} color="var(--mantine-color-teal-7)" aria-hidden />
        <Title order={1} ta="center">
          Order #{order.orderNumber} is {payment ? 'paid' : 'comped'}
        </Title>
        <Text c="dimmed" ta="center">
          {payment
            ? `${formatMoney(payment.amount)} by ${payment.tenderType === 'CASH' ? 'cash' : `card${payment.cardLast4 ? ` ending ${payment.cardLast4}` : ''}`}`
            : `Nothing to collect · approved by ${lastComp?.approvedBy.name ?? 'a manager'}`}
          {order.guestName ? ` · call out “${order.guestName}” at pickup` : ''}
        </Text>

        {payment?.changeDue && (
          <Paper w="100%" p="xl" radius="lg" bg="green.0" ta="center">
            <Text fw={700} c="green.9">Change due</Text>
            <Text fw={800} fz={64} lh={1.1} c="green.9" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {formatMoney(payment.changeDue)}
            </Text>
          </Paper>
        )}

        <Button size="xl" fullWidth onClick={onNextSale} autoFocus>
          Start next sale
        </Button>
      </Stack>
    </Center>
  );
}
