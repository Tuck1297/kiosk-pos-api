import { Box, Grid } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * Two columns from tablet width up (main work on the left, the order on the right, pinned);
 * one column on phones, with the order below.
 */
export function StepLayout({ main, aside }: { main: ReactNode; aside: ReactNode }) {
  return (
    <Grid gutter={{ base: 'md', md: 'xl' }}>
      <Grid.Col span={{ base: 12, sm: 7, lg: 8 }}>{main}</Grid.Col>
      <Grid.Col span={{ base: 12, sm: 5, lg: 4 }}>
        <Box className="step-aside">{aside}</Box>
      </Grid.Col>
    </Grid>
  );
}
