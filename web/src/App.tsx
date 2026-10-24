import { Alert, Box, Center, Group, Loader, Stepper, Text } from '@mantine/core';
import { IconBuildingStore } from '@tabler/icons-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, api, describeError, type Location, type MenuItem, type Order, type Payment } from './api/client';
import { addItem, changeQuantity, toLineRequests, type CartLine } from './cart';
import { DoneStep } from './steps/DoneStep';
import { ItemsStep } from './steps/ItemsStep';
import { PaymentStep } from './steps/PaymentStep';
import { ReviewStep } from './steps/ReviewStep';
import { useQuote } from './useQuote';

/** Which kiosk this register belongs to. Defaults to the seeded Lakeside Snack Stand. */
const LOCATION_ID = (import.meta.env.VITE_LOCATION_ID as string | undefined) ?? '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11';

const ITEMS = 0;
const REVIEW = 1;
const PAYMENT = 2;
const DONE = 3;

export function App() {
  const [step, setStep] = useState(ITEMS);
  const [location, setLocation] = useState<Location | null>(null);
  const [menu, setMenu] = useState<MenuItem[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [cart, setCart] = useState<CartLine[]>([]);
  const quote = useQuote(LOCATION_ID, cart);

  const [order, setOrder] = useState<Order | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  // Reused only if the network fails mid-request, so a retry can't create a second order.
  const createKey = useRef<string | null>(null);

  const loadMenu = useCallback(() => {
    setLoadError(null);
    Promise.all([api.location(LOCATION_ID), api.menu(LOCATION_ID)])
      .then(([loc, page]) => {
        setLocation(loc);
        setMenu(page.items);
      })
      .catch((e: unknown) => setLoadError(describeError(e)));
  }, []);

  useEffect(loadMenu, [loadMenu]);

  function editItems() {
    createKey.current = null;
    setCreateError(null);
    setStep(ITEMS);
  }

  async function placeOrder(guestName: string) {
    createKey.current ??= crypto.randomUUID();
    setCreating(true);
    setCreateError(null);
    try {
      const created = await api.createOrder(
        { locationId: LOCATION_ID, channel: 'COUNTER', guestName: guestName || undefined, lines: toLineRequests(cart) },
        createKey.current,
      );
      setOrder(created);
      setStep(PAYMENT);
    } catch (e) {
      if (e instanceof ApiError) {
        createKey.current = null;
      }
      setCreateError(describeError(e));
    } finally {
      setCreating(false);
    }
  }

  function nextSale() {
    setCart([]);
    setOrder(null);
    setPayment(null);
    createKey.current = null;
    setStep(ITEMS);
    loadMenu(); // pick up anything that sold out meanwhile
  }

  return (
    <Box mih="100vh" bg="var(--mantine-color-body)">
      <Group
        component="header"
        justify="space-between"
        wrap="wrap"
        gap="md"
        px="lg"
        py="sm"
        style={{ borderBottom: '1px solid var(--mantine-color-default-border)' }}
      >
        <Group gap="sm">
          <Center w={36} h={36} bg="indigo.8" style={{ borderRadius: 'var(--mantine-radius-md)' }}>
            <IconBuildingStore size={20} color="white" aria-hidden />
          </Center>
          <div>
            <Text fw={700} lh={1.2}>Register 1</Text>
            <Text size="sm" c="dimmed" lh={1.2}>{location?.name ?? 'Loading…'}</Text>
          </div>
        </Group>
        <Box style={{ flex: '1 1 420px', maxWidth: 560 }}>
          <Stepper active={step} size="sm" allowNextStepsSelect={false}>
            <Stepper.Step label="Items" />
            <Stepper.Step label="Review" />
            <Stepper.Step label="Payment" />
          </Stepper>
        </Box>
      </Group>

      <Box component="main" p="lg">
        {loadError && (
          <Alert color="red" variant="light" title="Can't load the menu" maw={640}>
            {loadError}
          </Alert>
        )}
        {!loadError && !menu && (
          <Center py="xl"><Loader /></Center>
        )}

        {menu && step === ITEMS && (
          <ItemsStep
            menu={menu}
            cart={cart}
            quote={quote}
            onAdd={(id) => setCart((c) => addItem(c, id))}
            onChangeQuantity={(id, delta) => setCart((c) => changeQuantity(c, id, delta))}
            onNext={() => setStep(REVIEW)}
          />
        )}
        {step === REVIEW && quote.quote && (
          <ReviewStep quote={quote.quote} busy={creating} error={createError} onBack={editItems} onConfirm={placeOrder} />
        )}
        {step === PAYMENT && order && (
          <PaymentStep
            order={order}
            onPaid={(p) => {
              setPayment(p);
              setStep(DONE);
            }}
          />
        )}
        {step === DONE && order && payment && (
          <DoneStep order={order} payment={payment} onNextSale={nextSale} />
        )}
      </Box>
    </Box>
  );
}
