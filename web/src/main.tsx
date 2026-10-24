import '@mantine/core/styles.css';

import { MantineProvider, createTheme } from '@mantine/core';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

const theme = createTheme({
  primaryColor: 'indigo',
  // Shade 8 keeps white text on filled buttons above 4.5:1 contrast.
  primaryShade: 8,
  defaultRadius: 'md',
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme}>
      <App />
    </MantineProvider>
  </StrictMode>,
);
