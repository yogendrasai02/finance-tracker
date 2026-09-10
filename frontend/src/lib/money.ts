/**
 * Money in this application is always a signed integer count of paise, the same as the backend's `BIGINT` columns.
 *
 * The rule (FRONTEND_CONVENTIONS §6.1): all arithmetic stays in integer paise.
 * The only place a value is divided by 100 is the display formatter below, and it does that with integer operations so no rounding can happen on the way.
 */

const RUPEE_GROUPING = new Intl.NumberFormat('en-IN', {
  style: 'decimal',
  useGrouping: true,
  maximumFractionDigits: 0,
})

/** Optional sign, digits with optional commas, and at most two decimal places. */
const RUPEE_INPUT_PATTERN = /^(?<sign>[-+])?(?<rupees>\d+)(?:\.(?<paise>\d{1,2}))?$/

const PAISE_PER_RUPEE = 100

export type ParsedAmount = { ok: true; paise: number } | { ok: false; reason: string }

/**
 * Renders integer paise as Indian currency, using lakh and crore grouping.
 *
 * Throws rather than guessing when the caller passes something that is not an exact integer, because a silently rounded amount on screen is worse than a visible failure.
 */
export function formatPaiseToInr(paise: number): string {
  if (!Number.isSafeInteger(paise)) {
    throw new RangeError(`Amount must be an exact integer number of paise, received ${paise}`)
  }

  const negative = paise < 0
  const absolute = Math.abs(paise)
  // `absolute % 100` is exact for integers, so `absolute - fraction` is an exact multiple of 100 and the division below is exact too.
  const fraction = absolute % PAISE_PER_RUPEE
  const rupees = (absolute - fraction) / PAISE_PER_RUPEE

  const formatted = `₹${RUPEE_GROUPING.format(rupees)}.${String(fraction).padStart(2, '0')}`
  return negative ? `-${formatted}` : formatted
}

/**
 * Turns what a user typed into integer paise.
 *
 * Returns a result rather than throwing, because invalid input is an expected state of a form and every caller has to show a message for it.
 * More than two decimal places is rejected instead of rounded: the user meant something, and the application should not decide what.
 */
export function parseInrToPaise(input: string): ParsedAmount {
  const cleaned = input.trim().replace(/[₹\s,]/g, '')

  if (cleaned === '') {
    return { ok: false, reason: 'Enter an amount.' }
  }

  const match = RUPEE_INPUT_PATTERN.exec(cleaned)
  if (!match?.groups) {
    return { ok: false, reason: 'Enter an amount like 1,234.56 with at most two decimal places.' }
  }

  const { sign, rupees, paise } = match.groups
  const wholePaise = Number(rupees) * PAISE_PER_RUPEE
  const fractionPaise = paise === undefined ? 0 : Number(paise.padEnd(2, '0'))
  const magnitude = wholePaise + fractionPaise

  if (!Number.isSafeInteger(magnitude)) {
    return { ok: false, reason: 'That amount is too large.' }
  }

  return { ok: true, paise: sign === '-' ? -magnitude : magnitude }
}
