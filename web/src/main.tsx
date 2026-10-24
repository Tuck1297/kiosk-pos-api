import '@fontsource-variable/bricolage-grotesque';
import '@fontsource-variable/lexend';
import '@mantine/core/styles.css';
import './theme.css';
import './responsive.css';

import { Button, MantineProvider, Paper, createTheme } from '@mantine/core';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

/**
 * "Park window" theme: navy ink on an airy cool canvas, lagoon teal for actions, marigold for
 * what's in the cart. Lexend (built for fast reading) for the interface; Bricolage Grotesque
 * for totals and headings. Category colors live in theme.css.
 */
const theme = createTheme({
  primaryColor: 'cyan',
  // cyan.9 (#0b7285): white text on filled buttons stays above 5:1 contrast.
  primaryShade: 9,
  black: '#1b2a41',
  fontFamily: "'Lexend Variable', system-ui, -apple-system, 'Segoe UI', sans-serif",
  headings: {
    fontFamily: "'Bricolage Grotesque Variable', 'Lexend Variable', system-ui, sans-serif",
    fontWeight: '750',
  },
  defaultRadius: 'md',
  radius: { xs: '6px', sm: '8px', md: '12px', lg: '18px', xl: '24px' },
  components: {
    Paper: Paper.extend({ defaultProps: { radius: 'lg' } }),
    Button: Button.extend({ defaultProps: { fw: 600 } }),
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme}>
      <App />
    </MantineProvider>
  </StrictMode>,
);
