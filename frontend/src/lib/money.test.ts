import { describe, expect, it } from 'vitest'

import { formatPaiseToInr, parseInrToPaise } from './money'

describe('formatPaiseToInr', () => {
  it.each([
    [0, '₹0.00'],
    [1, '₹0.01'],
    [99, '₹0.99'],
    [100, '₹1.00'],
    [105, '₹1.05'],
    [123456, '₹1,234.56'],
    [1234567890, '₹1,23,45,678.90'],
  ])('formats %i paise as %s', (paise, expected) => {
    expect(formatPaiseToInr(paise)).toBe(expected)
  })

  it('puts the sign in front of the symbol for an outflow', () => {
    expect(formatPaiseToInr(-123456)).toBe('-₹1,234.56')
    expect(formatPaiseToInr(-1)).toBe('-₹0.01')
  })

  it('treats negative zero as zero', () => {
    expect(formatPaiseToInr(-0)).toBe('₹0.00')
  })

  it('stays exact at the top of the safe integer range', () => {
    expect(formatPaiseToInr(Number.MAX_SAFE_INTEGER)).toBe('₹9,00,71,99,25,47,409.91')
  })

  it.each([1.5, Number.NaN, Number.POSITIVE_INFINITY, Number.MAX_SAFE_INTEGER + 2])(
    'refuses %p rather than displaying a rounded amount',
    (value) => {
      expect(() => formatPaiseToInr(value)).toThrow(RangeError)
    },
  )
})

describe('parseInrToPaise', () => {
  it.each([
    ['1234.56', 123456],
    ['1,234.56', 123456],
    ['₹1,234.56', 123456],
    ['  1234  ', 123400],
    ['1234.5', 123450],
    ['0.01', 1],
    ['0', 0],
    ['-12.34', -1234],
    ['+12.34', 1234],
  ])('parses %s to %i paise', (input, expected) => {
    expect(parseInrToPaise(input)).toEqual({ ok: true, paise: expected })
  })

  it.each(['', '   ', 'abc', '12.345', '12.', '.5', '1e3', '1,2,3.4.5', '--1'])(
    'rejects %p',
    (input) => {
      expect(parseInrToPaise(input).ok).toBe(false)
    },
  )

  it('gives a reason a form can show', () => {
    const result = parseInrToPaise('12.345')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.reason).toContain('two decimal places')
    }
  })

  it('rejects an amount too large to hold exactly', () => {
    expect(parseInrToPaise('99999999999999999999').ok).toBe(false)
  })

  it('round-trips through the formatter', () => {
    const parsed = parseInrToPaise('1,23,45,678.90')
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(formatPaiseToInr(parsed.paise)).toBe('₹1,23,45,678.90')
    }
  })
})
