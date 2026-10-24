import type { ReactNode } from 'react';

/**
 * Two columns from tablet width up (main work on the left, the order on the right, pinned);
 * one column on phones, with the order below. Column rules live in responsive.css.
 */
export function StepLayout({ main, aside }: { main: ReactNode; aside: ReactNode }) {
  return (
    <div className="step-layout">
      <div className="step-main">{main}</div>
      <div className="step-aside">{aside}</div>
    </div>
  );
}
