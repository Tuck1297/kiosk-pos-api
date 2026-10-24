import { Button, Center, Paper, Stack, Text, Title } from '@mantine/core';
import { IconCircleCheck } from '@tabler/icons-react';
import type { Order, Payment } from '../api/client';
import { formatMoney } from '../money';

interface Props {
  order: Order;
  payment: Payment;
  onNextSale: () => void;
}

export function DoneStep({ order, payment, onNextSale }: Props) {
  return (
    <Center py="xl">
      <Stack align="center" gap="lg" maw={560} w="100%">
        <IconCircleCheck size={72} stroke={1.5} color="var(--mantine-color-teal-7)" aria-hidden />
        <Title order={1} ta="center">Order #{order.orderNumber} is paid</Title>
        <Text c="dimmed" ta="center">
          {formatMoney(payment.amount)} by {payment.tenderType === 'CASH' ? 'cash' : `card${payment.cardLast4 ? ` ending ${payment.cardLast4}` : ''}`}
          {order.guestName ? ` · call out “${order.guestName}” at pickup` : ''}
        </Text>

        {payment.changeDue && (
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
