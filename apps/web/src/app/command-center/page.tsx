import { redirect } from 'next/navigation';

/** Command Center is the primary landing at "/" (UX §13, ADR-008). */
export default function CommandCenterRedirect() {
  redirect('/');
}
