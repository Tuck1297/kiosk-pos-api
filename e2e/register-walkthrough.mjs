// Drives the register through two real sales against the running stack and saves a screenshot of
// each screen, once per screen size (phones and tablets in both orientations, and desktop).
// Doubles as a browser end-to-end check: it fails if any step doesn't show what the server should
// have computed, or if the page scrolls sideways on any screen.
//
//   docker compose up -d --build --wait
//   cd e2e && npm install --no-save playwright@1.49.1 && npx playwright install chromium
//   node register-walkthrough.mjs            # BASE_URL=http://localhost:8080 OUT=screenshots
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const OUT = process.env.OUT ?? 'screenshots';

const SCREENS = {
  'phone-portrait': { viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true },
  'phone-landscape': { viewport: { width: 844, height: 390 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true },
  'tablet-portrait': { viewport: { width: 820, height: 1180 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
  'tablet-landscape': { viewport: { width: 1180, height: 820 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
  desktop: { viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 },
};
const only = process.env.SCREENS?.split(',');

const browser = await chromium.launch();
let failed = false;
for (const [screen, options] of Object.entries(SCREENS)) {
  if (only && !only.includes(screen)) continue;
  const context = await browser.newContext(options);
  const page = await context.newPage();
  try {
    await walkthrough(page, `${OUT}/${screen}`);
    console.log(`${screen}: ok`);
  } catch (e) {
    failed = true;
    await page.screenshot({ path: `${OUT}/${screen}/failure.png`, fullPage: true }).catch(() => {});
    console.error(`${screen}: FAILED`, e);
  } finally {
    await context.close();
  }
}
await browser.close();
if (failed) process.exitCode = 1;

async function walkthrough(page, dir) {
  await mkdir(dir, { recursive: true });
  page.setDefaultTimeout(15_000);
  const consoleErrors = [];
  page.on('console', (m) => m.type() === 'error' && consoleErrors.push(m.text()));

  let n = 0;
  async function shot(name) {
    await page.waitForTimeout(300); // let transitions settle
    // Nothing should make a phone scroll sideways.
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (overflow > 1) throw new Error(`${name}: page is ${overflow}px wider than the screen`);
    const file = `${dir}/${String(++n).padStart(2, '0')}-${name}.png`;
    await page.screenshot({ path: file, fullPage: true });
    console.log('saved', file);
  }

  // Phones show some actions twice (the checkout bar and the summary); either one will do.
  const button = (name) => page.getByRole('button', { name }).first();
  async function pricesSettled() {
    await page.getByText('Updating prices').first().waitFor({ state: 'hidden' });
  }
  async function addItem(name, times = 1) {
    for (let i = 0; i < times; i++) {
      await button(new RegExp(`^Add ${name},`)).click();
    }
  }

  // Sign in
  await page.goto(BASE);
  await page.getByLabel('Employee ID').fill('C2001');
  await page.getByLabel('PIN').fill('1111');
  await shot('sign-in');
  await page.getByLabel('PIN').press('Enter');

  // Sale 1: promo code, a declined card, then a keyed card
  await button(/^Sign out/).waitFor();
  await page.getByText('Soft Pretzel').first().waitFor();
  await shot('menu');

  await addItem('Soft Pretzel', 2);
  await addItem('Frozen Lemonade');
  await pricesSettled();
  await page.getByText('$22.35').first().waitFor(); // 2099 + 6.5% tax
  await shot('cart');

  await button('Review order').click();
  await page.getByLabel('Guest name').fill('Maya');
  await page.getByPlaceholder('e.g. PARKFAN10').fill('SUMMER25');
  await button('Apply').click();
  await page.getByText(/expired/).waitFor();
  await shot('review-expired-code');

  await page.getByPlaceholder('e.g. PARKFAN10').fill('PARKFAN10');
  await button('Apply').click();
  await page.getByText('$20.12').first().waitFor(); // 10% off before tax
  await pricesSettled();
  await shot('review-promo-code');

  await button('Continue to payment').click();
  await page.getByText('Tap, insert or swipe').waitFor();
  await shot('payment-card-reader');

  await button(/^Visa ending 0002/).click();
  await page.getByText('Declined', { exact: true }).waitFor();
  await shot('card-declined');

  await button('Key in card number').click();
  await page.getByLabel('Card number').fill('4242424242424242');
  await page.getByLabel('Expiry').fill('1229');
  await page.getByLabel('CVC').fill('123');
  await shot('key-in-card');

  await button('Charge card').click();
  await page.getByText('Approved', { exact: true }).waitFor();
  await shot('card-approved');

  await page.getByText(/is paid/).waitFor();
  await page.getByText(/Visa ending 4242/).waitFor();
  await shot('paid-by-card');

  // Sale 2: employee discount, the comp dialog, then cash
  await button('Start next sale').click();
  await addItem('Smoked Turkey Leg');
  await addItem('Iced Cold Brew');
  await pricesSettled();
  await button('Review order').click();
  await page.getByText('Employee ID', { exact: true }).click();
  await page.getByPlaceholder('e.g. C2001').fill('C2001');
  await button('Apply').click();
  await page.getByText(/Employee discount/).first().waitFor();
  await pricesSettled();
  await shot('review-employee-discount');

  await button('Continue to payment').click();
  await button('Comp…').click();
  await page.getByText('Comp with manager approval').waitFor();
  await shot('comp-dialog');
  await button('Cancel').click();

  await page.getByRole('radio', { name: /^Cash/ }).click();
  await page.getByRole('group', { name: 'Quick cash amounts' }).getByRole('button').last().click();
  await shot('payment-cash');

  await button('Complete cash sale').click();
  await page.getByText('Change due').waitFor();
  await shot('paid-cash-change');

  if (consoleErrors.length) {
    console.log('Browser console errors:\n' + consoleErrors.join('\n'));
  }
}
