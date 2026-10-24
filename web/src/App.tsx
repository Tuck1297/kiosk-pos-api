import { ActionIcon, Alert, Badge, Box, Button, Center, Group, Loader, Stack, Stepper, Text } from '@mantine/core';
import { IconBuildingStore, IconLogout } from '@tabler/icons-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ApiError, api, describeError, setUnauthorizedHandler,
  type DiscountRequest, type Location, type MenuItem, type Order, type Payment, type Staff,
} from './api/client';
import { addItem, changeQuantity, toLineRequests, type CartLine } from './cart';
import { SignInScreen } from './components/SignInScreen';
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
const STEP_NAMES = ['Items', 'Review', 'Payment', 'Done'];

/**
 * Signed out: the sign-in screen. Signed in: the register. The session itself lives in an
 * HttpOnly cookie this code can't read, so "am I signed in?" is answered by GET /auth/me.
 */
export function App() {
  // undefined while checking, null when signed out
  const [staff, setStaff] = useState<Staff | null | undefined>(undefined);

  useEffect(() => {
    // Any 401 (e.g. the session expired mid-shift) returns to the sign-in screen.
    setUnauthorizedHandler(() => setStaff(null));
    api.me().then(setStaff).catch(() => setStaff(null));
  }, []);

  async function signOut() {
    try {
      await api.logout();
    } finally {
      setStaff(null);
    }
  }

  if (staff === undefined) {
    return <Center mih="100dvh"><Loader /></Center>;
  }
  if (staff === null) {
    return <SignInScreen onSignedIn={setStaff} />;
  }
  return <Register staff={staff} onSignOut={signOut} />;
}

function Register({ staff, onSignOut }: { staff: Staff; onSignOut: () => void }) {
  const [step, setStep] = useState(ITEMS);
  const [location, setLocation] = useState<Location | null>(null);
  const [menu, setMenu] = useState<MenuItem[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [cart, setCart] = useState<CartLine[]>([]);
  const [discount, setDiscount] = useState<DiscountRequest | null>(null);
  const [discountNotice, setDiscountNotice] = useState<string | null>(null);
  const quote = useQuote(LOCATION_ID, cart, discount);

  // A discount can stop applying when the cart changes (e.g. below a code's minimum). Take it
  // off and say why, rather than leaving the order unpriceable.
  useEffect(() => {
    if (discount && quote.errorCode === 'DISCOUNT_NOT_APPLICABLE') {
      setDiscountNotice(`Discount removed. ${quote.error ?? ''}`.trim());
      setDiscount(null);
    }
  }, [discount, quote.errorCode, quote.error]);

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

  /** Asks the server whether the discount applies to this cart before showing it as applied. */
  async function applyDiscount(next: DiscountRequest): Promise<string | null> {
    try {
      await api.quote({ locationId: LOCATION_ID, lines: toLineRequests(cart), discount: next });
      setDiscountNotice(null);
      setDiscount(next);
      return null;
    } catch (e) {
      return describeError(e);
    }
  }

  function removeDiscount() {
    createKey.current = null;
    setCreateError(null);
    setDiscount(null);
  }

  async function placeOrder(guestName: string) {
    createKey.current ??= crypto.randomUUID();
    setCreating(true);
    setCreateError(null);
    try {
      const created = await api.createOrder(
        {
          locationId: LOCATION_ID,
          channel: 'COUNTER',
          guestName: guestName || undefined,
          lines: toLineRequests(cart),
          discount: discount ?? undefined,
        },
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
    setDiscount(null);
    setDiscountNotice(null);
    setOrder(null);
    setPayment(null);
    createKey.current = null;
    setStep(ITEMS);
    loadMenu(); // pick up anything that sold out meanwhile
  }

  return (
    <Box mih="100dvh" className="register-shell">
      <Group
        component="header"
        justify="space-between"
        wrap="nowrap"
        gap="sm"
        px={{ base: 'sm', sm: 'lg' }}
        py={{ base: 6, sm: 'sm' }}
        bg="white"
        style={{ borderBottom: '1px solid var(--register-line)' }}
      >
        <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
          <Center w={38} h={38} bg="var(--register-ink)" style={{ borderRadius: 'var(--mantine-radius-md)', flexShrink: 0 }}>
            <IconBuildingStore size={20} color="white" aria-hidden />
          </Center>
          <Box style={{ minWidth: 0 }}>
            <Text fw={800} lh={1.2} ff="heading" fz="lg">Register 1</Text>
            <Text size="sm" c="dimmed" lh={1.2} truncate="end">{location?.name ?? 'Loading…'}</Text>
          </Box>
        </Group>

        {/* Full stepper where there's room; a compact step badge on phones and small tablets. */}
        <Box visibleFrom="md" style={{ flex: '1 1 420px', maxWidth: 560 }}>
          <Stepper active={step} size="sm" allowNextStepsSelect={false}>
            <Stepper.Step label="Items" />
            <Stepper.Step label="Review" />
            <Stepper.Step label="Payment" />
          </Stepper>
        </Box>
        <Badge hiddenFrom="md" variant="light" size="lg" tt="none" style={{ flexShrink: 0 }}>
          {step < DONE ? `${STEP_NAMES[step]}, ${step + 1} of 3` : 'Done'}
        </Badge>

        <Group gap="sm" wrap="nowrap" style={{ flexShrink: 0 }}>
          <Box visibleFrom="sm" style={{ textAlign: 'right' }}>
            <Text fw={600} lh={1.2}>{staff.name}</Text>
            <Text size="sm" c="dimmed" lh={1.2}>{staff.role === 'MANAGER' ? 'Manager' : 'Cashier'} {staff.employeeId}</Text>
          </Box>
          <Button visibleFrom="sm" variant="default" leftSection={<IconLogout size={16} />} onClick={onSignOut}
                  disabled={step === PAYMENT}>
            Sign out
          </Button>
          <ActionIcon hiddenFrom="sm" variant="default" size="xl" onClick={onSignOut} disabled={step === PAYMENT}
                      aria-label={`Sign out ${staff.name}`}>
            <IconLogout size={20} />
          </ActionIcon>
        </Group>
      </Group>

      <Box
        component="main"
        className="register-main"
        p={{ base: 'sm', sm: 'lg' }}
      >
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
          <ReviewStep
            quote={quote.quote}
            repricing={quote.loading}
            discount={discount}
            discountNotice={discountNotice}
            onApplyDiscount={applyDiscount}
            onRemoveDiscount={removeDiscount}
            busy={creating}
            error={createError}
            onBack={editItems}
            onConfirm={placeOrder}
          />
        )}
        {step === REVIEW && !quote.quote && quote.error && (
          <Alert color="red" variant="light" title="Can't price this order" maw={640}>
            <Stack gap="sm" align="flex-start">
              <Text>{quote.error}</Text>
              <Button variant="default" onClick={editItems}>Edit items</Button>
            </Stack>
          </Alert>
        )}
        {step === PAYMENT && order && (
          <PaymentStep
            order={order}
            onPaid={(p) => {
              setPayment(p);
              setStep(DONE);
            }}
            onOrderChange={setOrder}
            onCompedInFull={(o) => {
              setOrder(o);
              setPayment(null);
              setStep(DONE);
            }}
          />
        )}
        {step === DONE && order && (
          <DoneStep order={order} payment={payment} onNextSale={nextSale} />
        )}
      </Box>
    </Box>
  );
}
