/**
 * Runtime polyfills — imported FIRST in main.tsx before any third-party code.
 *
 * Why this file exists:
 *   framer-motion, recharts, and lucide-react ship minified builds that call
 *   Array.prototype.at() and String.prototype.at() internally. These are ES2022
 *   methods absent in:
 *     - Safari < 15.4  (released March 2022 — covers all of iOS 14 / early iOS 15)
 *     - Android WebViews tied to Chrome < 92 engine builds
 *     - Some Samsung Internet and UC Browser variants
 *
 *   Without this file those browsers crash at auth-page render with
 *   "TypeError: undefined is not a function" before any React tree mounts.
 *
 * Contract:
 *   - Only patches if the native implementation is absent.
 *   - Uses Object.defineProperty with enumerable:false to match native behaviour
 *     (native .at() is non-enumerable; a plain assignment would make it enumerable
 *      and break for…in loops on arrays in host code).
 *   - Handles positive indexes, negative indexes, and out-of-bounds (→ undefined)
 *     per the ECMAScript 2022 spec §22.1.3.1.
 *   - Zero dependencies. No dynamic imports. Synchronous. < 500 bytes minified.
 */

/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Shared implementation for both Array and String `.at()`.
 * `this` is typed as the minimal indexed-collection interface both share.
 */
function _atPolyfill(
  this: { length: number; [index: number]: any },
  index: number,
): any {
  const len = this.length;
  // Math.trunc handles floats; `|| 0` converts NaN/undefined/null to 0.
  const k = Math.trunc(index) || 0;
  // Negative index: count from the end.
  const i = k < 0 ? len + k : k;
  if (i < 0 || i >= len) return undefined;
  return this[i];
}

if (typeof (Array.prototype as any).at !== 'function') {
  Object.defineProperty(Array.prototype, 'at', {
    value: _atPolyfill,
    writable: true,
    enumerable: false,
    configurable: true,
  });
}

if (typeof (String.prototype as any).at !== 'function') {
  Object.defineProperty(String.prototype, 'at', {
    value: _atPolyfill,
    writable: true,
    enumerable: false,
    configurable: true,
  });
}

// TypedArray.prototype.at — the gap that crashed registration.
// All 11 TypedArray constructors (Int8Array, Uint8Array, Float64Array, …) share
// one hidden intrinsic prototype, %TypedArray%.prototype, reachable as the
// prototype of any concrete TypedArray's prototype. Patching that single object
// covers every TypedArray variant at once. TypedArrays already expose `length`
// and integer-indexed access, so the shared _atPolyfill applies unchanged.
// Guarded so it is a no-op in hosts lacking TypedArrays or already shipping .at().
if (typeof Int8Array === 'function') {
  const TypedArrayProto = Object.getPrototypeOf(Int8Array.prototype);
  if (TypedArrayProto && typeof (TypedArrayProto as any).at !== 'function') {
    Object.defineProperty(TypedArrayProto, 'at', {
      value: _atPolyfill,
      writable: true,
      enumerable: false,
      configurable: true,
    });
  }
}
